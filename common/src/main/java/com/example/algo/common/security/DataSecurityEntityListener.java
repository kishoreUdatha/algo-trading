package com.example.algo.common.security;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DataSecurityEntityListener {

  @PrePersist
  public void prePersist(Object entity) {
    if (entity instanceof UserOwnedEntity) {
      UserOwnedEntity userEntity = (UserOwnedEntity) entity;
      UserContext currentUser = UserContext.getCurrentUser();

      // Automatically set the owner
      if (userEntity.getUserId() == null) {
        userEntity.setUserId(currentUser.getUserId());
      } else {
        // Validate user can only create entities for themselves
        currentUser.validateAccess(userEntity.getUserId());
      }

      if (userEntity.getTenantId() == null) {
        userEntity.setTenantId(currentUser.getTenantId());
      }

      log.debug(
          "Pre-persist: Set userId={}, tenantId={} for entity {}",
          userEntity.getUserId(),
          userEntity.getTenantId(),
          entity.getClass().getSimpleName());
    }
  }

  @PreUpdate
  public void preUpdate(Object entity) {
    if (entity instanceof UserOwnedEntity) {
      UserOwnedEntity userEntity = (UserOwnedEntity) entity;
      UserContext currentUser = UserContext.getCurrentUser();

      // Validate user can only update their own entities
      currentUser.validateAccess(userEntity.getUserId());

      log.debug(
          "Pre-update: Validated access for userId={} on entity {}",
          userEntity.getUserId(),
          entity.getClass().getSimpleName());
    }
  }
}
