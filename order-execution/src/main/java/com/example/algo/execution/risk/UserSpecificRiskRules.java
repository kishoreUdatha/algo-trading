package com.example.algo.execution.risk;

import com.example.algo.common.events.StrategySignalEvent;
import com.example.algo.common.security.UserContext;
import com.example.algo.execution.repository.UserPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserSpecificRiskRules {

    private final RedisTemplate<String, String> redis;
    private final UserPositionRepository positionRepository;

    public RiskResult checkUserSpecificRisk(StrategySignalEvent event) {
        UserContext user = UserContext.getCurrentUser();
        String userId = user.getUserId();

        try {
            // 1. User-specific order value limit
            RiskResult orderValueCheck = checkUserOrderValueLimit(event, userId);
            if (!orderValueCheck.ok()) return orderValueCheck;

            // 2. User-specific rate limiting
            RiskResult rateLimitCheck = checkUserRateLimit(event, userId);
            if (!rateLimitCheck.ok()) return rateLimitCheck;

            // 3. User-specific daily loss limit
            RiskResult dailyLossCheck = checkUserDailyLoss(event, userId);
            if (!dailyLossCheck.ok()) return dailyLossCheck;

            // 4. User-specific position limits
            RiskResult positionCheck = checkUserPositionLimit(event, userId);
            if (!positionCheck.ok()) return positionCheck;

            // 5. User account status check
            RiskResult accountCheck = checkUserAccountStatus(userId);
            if (!accountCheck.ok()) return accountCheck;

            log.info("All risk checks passed for user {} on symbol {}", userId, event.getSymbol());
            return new RiskResult(true, "OK", "All user-specific risk checks passed");

        } catch (Exception e) {
            log.error("Risk check failed for user {}: {}", userId, e.getMessage());
            return new RiskResult(false, "RISK_CHECK_ERROR", "Risk validation failed: " + e.getMessage());
        }
    }

    private RiskResult checkUserOrderValueLimit(StrategySignalEvent event, String userId) {
        // Get user-specific limits (could be from database or config)
        double userMaxOrderValue = getUserMaxOrderValue(userId);
        double orderValue = event.getPrice() * getQuantityForUser(event, userId);

        if (orderValue > userMaxOrderValue) {
            return new RiskResult(false, "USER_ORDER_VALUE_EXCEEDED",
                    String.format("User %s order value %.2f exceeds limit %.2f",
                            userId, orderValue, userMaxOrderValue));
        }

        return new RiskResult(true, "OK", "User order value check passed");
    }

    private RiskResult checkUserRateLimit(StrategySignalEvent event, String userId) {
        String key = "user_rate_limit:" + userId + ":" + event.getSymbol();
        String countKey = key + ":count";
        String timeKey = key + ":time";

        long currentTime = System.currentTimeMillis();
        long lastTime = Long.parseLong(redis.opsForValue().get(timeKey) != null ?
                redis.opsForValue().get(timeKey) : "0");

        int userMaxOrdersPerMinute = getUserMaxOrdersPerMinute(userId);

        // Reset counter if more than a minute has passed
        if (currentTime - lastTime > 60000) {
            redis.opsForValue().set(countKey, "0");
            redis.opsForValue().set(timeKey, String.valueOf(currentTime));
        }

        int currentCount = Integer.parseInt(redis.opsForValue().get(countKey) != null ?
                redis.opsForValue().get(countKey) : "0");

        if (currentCount >= userMaxOrdersPerMinute) {
            return new RiskResult(false, "USER_RATE_LIMIT_EXCEEDED",
                    String.format("User %s rate limit exceeded: %d orders in last minute",
                            userId, currentCount));
        }

        // Increment counter
        redis.opsForValue().increment(countKey);
        redis.expire(countKey, Duration.ofMinutes(2));
        redis.expire(timeKey, Duration.ofMinutes(2));

        return new RiskResult(true, "OK", "User rate limit check passed");
    }

    private RiskResult checkUserDailyLoss(StrategySignalEvent event, String userId) {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String lossKey = "user_daily_loss:" + userId + ":" + today;

        double userMaxDailyLoss = getUserMaxDailyLoss(userId);
        double currentLoss = Double.parseDouble(redis.opsForValue().get(lossKey) != null ?
                redis.opsForValue().get(lossKey) : "0.0");

        if (Math.abs(currentLoss) > userMaxDailyLoss) {
            return new RiskResult(false, "USER_DAILY_LOSS_EXCEEDED",
                    String.format("User %s daily loss limit exceeded: %.2f", userId, currentLoss));
        }

        return new RiskResult(true, "OK", "User daily loss check passed");
    }

    private RiskResult checkUserPositionLimit(StrategySignalEvent event, String userId) {
        double userPositionLimit = getUserPositionLimit(userId);

        // Get current user position value
        Double currentPositionValue = positionRepository.getTotalPnLForCurrentUser();
        double positionValue = currentPositionValue != null ? Math.abs(currentPositionValue) : 0.0;

        double orderValue = event.getPrice() * getQuantityForUser(event, userId);
        double newPositionValue = positionValue + orderValue;

        if (newPositionValue > userPositionLimit) {
            return new RiskResult(false, "USER_POSITION_LIMIT_EXCEEDED",
                    String.format("User %s position limit exceeded: %.2f > %.2f",
                            userId, newPositionValue, userPositionLimit));
        }

        return new RiskResult(true, "OK", "User position limit check passed");
    }

    private RiskResult checkUserAccountStatus(String userId) {
        // Check if user account is active and in good standing
        String statusKey = "user_account_status:" + userId;
        String status = redis.opsForValue().get(statusKey);

        if ("SUSPENDED".equals(status) || "BLOCKED".equals(status)) {
            return new RiskResult(false, "USER_ACCOUNT_SUSPENDED",
                    "User account is suspended or blocked");
        }

        return new RiskResult(true, "OK", "User account status check passed");
    }

    // Helper methods to get user-specific limits (could be from database or config service)
    private double getUserMaxOrderValue(String userId) {
        // Implementation would fetch from user settings or use defaults based on user tier
        return 50000.0; // Default value
    }

    private int getUserMaxOrdersPerMinute(String userId) {
        // Implementation would fetch from user settings
        return 5; // Conservative default
    }

    private double getUserMaxDailyLoss(String userId) {
        // Implementation would fetch from user settings
        return 10000.0; // Default value
    }

    private double getUserPositionLimit(String userId) {
        // Implementation would fetch from user settings
        return 500000.0; // Default value
    }

    private int getQuantityForUser(StrategySignalEvent event, String userId) {
        // User-specific quantity calculation
        return 1; // Simple default
    }

    public void updateUserDailyPnL(String userId, double pnl) {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String lossKey = "user_daily_loss:" + userId + ":" + today;
        redis.opsForValue().increment(lossKey, -pnl); // Negative PnL increases loss
        redis.expire(lossKey, Duration.ofDays(2));
    }
}