package com.example.algo.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSessionService {

    private final RedisTemplate<String, String> redis;

    // In-memory cache for frequently accessed sessions (with Redis as backup)
    private final ConcurrentHashMap<String, SessionInfo> sessionCache = new ConcurrentHashMap<>();

    public String createSession(String userId, String tenantId) {
        String sessionId = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();

        SessionInfo session = new SessionInfo(sessionId, userId, tenantId, now, now);

        // Store in Redis with expiration
        String sessionKey = "session:" + sessionId;
        String sessionData = userId + ":" + tenantId + ":" + now;
        redis.opsForValue().set(sessionKey, sessionData, Duration.ofHours(24));

        // Cache locally for performance
        sessionCache.put(sessionId, session);

        // Limit concurrent sessions per user
        limitUserSessions(userId);

        log.info("Created session {} for user {} in tenant {}", sessionId, userId, tenantId);
        return sessionId;
    }

    public boolean isSessionActive(String sessionId) {
        if (sessionId == null) {
            return false;
        }

        long now = Instant.now().toEpochMilli();

        // Check local cache first
        SessionInfo cached = sessionCache.get(sessionId);
        if (cached != null && cached.lastAccessed() > (now - Duration.ofMinutes(30).toMillis())) {
            // Replace with updated record instead of mutating
            sessionCache.put(sessionId, cached.withUpdatedLastAccessed());
            return true;
        }

        // Check Redis
        String sessionKey = "session:" + sessionId;
        String sessionData = redis.opsForValue().get(sessionKey);

        if (sessionData != null) {
            // Refresh session expiry
            redis.expire(sessionKey, Duration.ofHours(24));

            // Update cache
            String[] parts = sessionData.split(":");
            long created = Long.parseLong(parts[2]);
            SessionInfo session = new SessionInfo(sessionId, parts[0], parts[1], created, now);
            sessionCache.put(sessionId, session);

            return true;
        }

        // Clean up cache
        sessionCache.remove(sessionId);
        return false;
    }

    public void invalidateSession(String sessionId) {
        if (sessionId != null) {
            redis.delete("session:" + sessionId);
            sessionCache.remove(sessionId);
            log.info("Invalidated session {}", sessionId);
        }
    }

    public void invalidateAllUserSessions(String userId) {
        Set<String> sessionKeys = redis.keys("session:*");

        for (String key : sessionKeys) {
            String sessionData = redis.opsForValue().get(key);
            if (sessionData != null && sessionData.startsWith(userId + ":")) {
                redis.delete(key);
                String sessionId = key.substring("session:".length());
                sessionCache.remove(sessionId);
            }
        }

        log.info("Invalidated all sessions for user {}", userId);
    }

    private void limitUserSessions(String userId) {
        Set<String> sessionKeys = redis.keys("session:*");
        int userSessionCount = 0;

        for (String key : sessionKeys) {
            String sessionData = redis.opsForValue().get(key);
            if (sessionData != null && sessionData.startsWith(userId + ":")) {
                userSessionCount++;

                if (userSessionCount > 5) {
                    redis.delete(key);
                    String sessionId = key.substring("session:".length());
                    sessionCache.remove(sessionId);
                    log.info("Removed excess session for user {}", userId);
                }
            }
        }
    }

    // ✅ record with epoch timestamps
    private record SessionInfo(String sessionId, String userId, String tenantId,
                               long created, long lastAccessed) {
        SessionInfo withUpdatedLastAccessed() {
            return new SessionInfo(sessionId, userId, tenantId, created, Instant.now().toEpochMilli());
        }
    }
}
