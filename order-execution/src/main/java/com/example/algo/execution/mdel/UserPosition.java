package com.example.algo.execution.mdel;

import com.example.algo.common.security.DataSecurityEntityListener;
import com.example.algo.common.security.UserOwnedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "user_positions",
        indexes = {
                @Index(name = "idx_user_tenant_symbol", columnList = "user_id, tenant_id, symbol", unique = true),
                @Index(name = "idx_user_positions_updated", columnList = "user_id, last_updated"),
                @Index(name = "idx_tenant_positions", columnList = "tenant_id, last_updated")
        })
@EntityListeners(DataSecurityEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPosition implements UserOwnedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String userId;

    @Column(nullable = false, length = 50)
    private String tenantId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private Long quantity = 0L;

    @Column(precision = 15, scale = 4)
    private BigDecimal averagePrice = BigDecimal.ZERO;

    @Column(precision = 15, scale = 4)
    private BigDecimal realizedPnL = BigDecimal.ZERO;

    @Column(precision = 15, scale = 4)
    private BigDecimal unrealizedPnL = BigDecimal.ZERO;

    @Column(nullable = false)
    private Instant lastUpdated;

    @Version
    private Long version;
}