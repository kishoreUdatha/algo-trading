package com.example.algo.broker.auth;

import com.example.algo.common.security.DataSecurityEntityListener;
import com.example.algo.common.security.UserOwnedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity
@Table(name = "user_broker_tokens", indexes = {
        @Index(name = "idx_user_tenant_broker", columnList = "user_id, tenant_id, broker", unique = true),
        @Index(name = "idx_tenant_broker", columnList = "tenant_id, broker"),
        @Index(name = "idx_user_broker_updated", columnList = "user_id, broker, updated_at")
})
@EntityListeners(DataSecurityEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBrokerToken implements UserOwnedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String userId;

    @Column(nullable = false, length = 50)
    private String tenantId;

    @Column(nullable = false, length = 20)
    private String broker;

    @Column(length = 4096)
    private String accessToken;

    @Column(length = 4096)
    private String refreshToken;

    private Instant accessTokenExpiry;
    private Instant refreshTokenExpiry;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version; // For optimistic locking
}
