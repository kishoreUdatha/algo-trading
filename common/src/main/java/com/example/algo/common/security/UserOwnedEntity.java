package com.example.algo.common.security;

public interface UserOwnedEntity {
    String getUserId();
    void setUserId(String userId);
    String getTenantId();
    void setTenantId(String tenantId);
}