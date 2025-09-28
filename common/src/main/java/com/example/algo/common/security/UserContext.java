package com.example.algo.common.security;

import java.util.Set;
import lombok.Builder;
import lombok.Data;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Data
@Builder
public class UserContext {
    private String userId;
    private String tenantId;
    private String sessionId;
    private Set<String> permissions;
    private String brokerIds; // Comma-separated list of authorized brokers

    public static UserContext getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) auth.getPrincipal()).getUserContext();
        }
        throw new SecurityException("No authenticated user found");
    }

    public void validateAccess(String resourceUserId) {
        if (!this.userId.equals(resourceUserId)) {
            throw new SecurityException("Access denied: User " + this.userId +
                    " cannot access resources of user " + resourceUserId);
        }
    }

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    public boolean canAccessBroker(String brokerName) {
        return brokerIds != null && brokerIds.contains(brokerName);
    }
}
