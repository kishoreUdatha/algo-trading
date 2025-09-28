package com.example.algo.execution.mdel;

import com.example.algo.common.security.DataSecurityEntityListener;
import com.example.algo.common.security.UserOwnedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "user_order_audit",
        indexes = {
                @Index(name = "idx_user_tenant_trade", columnList = "user_id, tenant_id, trade_id"),
                @Index(name = "idx_user_symbol_created", columnList = "user_id, symbol, created_at"),
                @Index(name = "idx_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_tenant_created", columnList = "tenant_id, created_at")
        })
@EntityListeners(DataSecurityEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOrderAudit implements UserOwnedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String userId;

    @Column(nullable = false, length = 50)
    private String tenantId;

    @Column(nullable = false, length = 50)
    private String tradeId;

    @Column(nullable = false, length = 30)
    private String eventType;

    @Column(length = 100)
    private String orderId;

    @Column(length = 50)
    private String strategy;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(length = 10)
    private String side;

    private Integer quantity;

    @Column(precision = 12, scale = 4)
    private BigDecimal price;

    @Column(length = 20)
    private String status;

    @Column(length = 20)
    private String broker;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(columnDefinition = "JSONB")
    private String metadata;
}
