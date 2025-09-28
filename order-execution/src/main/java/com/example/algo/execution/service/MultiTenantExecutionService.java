package com.example.algo.execution.service;

import com.example.algo.broker.core.BrokerClient;
import com.example.algo.broker.core.BrokerRouter;
import com.example.algo.broker.service.UserBrokerAuthService;
import com.example.algo.common.events.StrategySignalEvent;
import com.example.algo.common.model.Order;
import com.example.algo.common.model.enums.OrderSide;
import com.example.algo.common.model.enums.OrderType;
import com.example.algo.common.security.UserContext;
import com.example.algo.execution.mdel.UserOrderAudit;
import com.example.algo.execution.mdel.UserPosition;
import com.example.algo.execution.repository.UserOrderAuditRepository;
import com.example.algo.execution.repository.UserPositionRepository;
import com.example.algo.execution.risk.EnhancedRiskRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MultiTenantExecutionService {

    private final BrokerRouter brokerRouter;
    private final EnhancedRiskRules riskRules;
    private final UserOrderAuditRepository auditRepository;
    private final UserPositionRepository positionRepository;
    private final UserBrokerAuthService brokerAuthService;

    @Transactional
    public Mono<Optional<Order>> placeOrderForUser(StrategySignalEvent signal, String userId) {
        UserContext currentUser = UserContext.getCurrentUser();

        // Security check: Ensure the current user can place orders for the requested user
        if (!currentUser.getUserId().equals(userId) && !currentUser.hasPermission("PLACE_ORDER_FOR_OTHERS")) {
            throw new SecurityException("User " + currentUser.getUserId() +
                    " cannot place orders for user " + userId);
        }

        return Mono.fromCallable(() -> {
                    String tradeId = generateTradeId(userId);

                    // Log order attempt with user context
                    logOrderAttempt(tradeId, signal, userId);

                    // Determine broker for this specific user
                    String brokerName = determineBrokerForUser(signal.getSymbol(), userId);

                    // Validate user has access to this broker
                    if (!currentUser.canAccessBroker(brokerName)) {
                        throw new SecurityException("User does not have access to broker: " + brokerName);
                    }

                    // Build order with user-specific context
                    Order order = buildUserOrder(signal, tradeId, userId);

                    return new OrderExecutionContext(brokerName, order, signal, userId, tradeId);
                })
                .flatMap(this::executeUserOrder)
                .doOnSuccess(result -> {
                    if (result.isPresent()) {
                        updateUserPosition(result.get(), userId);
                        logOrderSuccess(result.get(), userId);
                    }
                })
                .doOnError(error -> logOrderFailure(userId, error.getMessage()));
    }

    private Mono<Optional<Order>> executeUserOrder(OrderExecutionContext context) {
        try {
            // Get user-specific broker client with their credentials
            var client = brokerRouter.byName(context.brokerName());

            // Execute order with user-specific authentication
            return executeWithUserAuth(client, context.order(), context.userId())
                    .map(Optional::of)
                    .onErrorReturn(Optional.empty());

        } catch (Exception e) {
            log.error("Failed to execute order for user {}: {}", context.userId(), e.getMessage());
            return Mono.just(Optional.empty());
        }
    }

    private Mono<Order> executeWithUserAuth(BrokerClient client, Order order, String userId) {
        // This would use user-specific broker authentication
        return brokerAuthService.executeWithUserCredentials(client, order, userId);
    }

    private String determineBrokerForUser(String symbol, String userId) {
        // User-specific broker routing logic
        // This could be based on user preferences, broker availability, etc.
        UserContext user = UserContext.getCurrentUser();

        // Get user's preferred broker for this symbol
        String preferredBroker = getUserPreferredBroker(userId, symbol);

        // Validate user has access to this broker
        if (user.canAccessBroker(preferredBroker)) {
            return preferredBroker;
        }

        // Fallback to first available broker for user
        return user.getBrokerIds().split(",")[0];
    }

    private String getUserPreferredBroker(String userId, String symbol) {
        // Implementation would query user preferences
        // For now, return a default
        return "zerodha";
    }

    private Order buildUserOrder(StrategySignalEvent signal, String tradeId, String userId) {
        return Order.builder()
                .id(tradeId)
                .symbol(signal.getSymbol())
                .side(signal.getSide())
                .qty(calculateQuantityForUser(signal, userId))
                .type(OrderType.MARKET)
                .price(signal.getPrice())
                .status("NEW")
                .build();
    }

    private int calculateQuantityForUser(StrategySignalEvent signal, String userId) {
        // User-specific position sizing
        // This could be based on user's capital, risk tolerance, etc.
        UserPosition existingPosition = positionRepository
                .findBySymbolForCurrentUser(signal.getSymbol())
                .orElse(null);

        // Simple implementation - could be much more sophisticated
        return 1;
    }

    private void updateUserPosition(Order order, String userId) {
        try {
            UserPosition position = positionRepository
                    .findBySymbolForCurrentUser(order.getSymbol())
                    .orElse(UserPosition.builder()
                            .userId(userId)
                            .tenantId(UserContext.getCurrentUser().getTenantId())
                            .symbol(order.getSymbol())
                            .quantity(0L)
                            .averagePrice(BigDecimal.ZERO)
                            .lastUpdated(Instant.now())
                            .build());

            // Update position based on order
            long newQuantity = position.getQuantity() +
                    (order.getSide() == OrderSide.BUY ? order.getQty() : -order.getQty());

            position.setQuantity(newQuantity);
            position.setLastUpdated(Instant.now());

            positionRepository.save(position);

            log.info("Updated position for user {} symbol {}: quantity={}",
                    userId, order.getSymbol(), newQuantity);

        } catch (Exception e) {
            log.error("Failed to update position for user {}: {}", userId, e.getMessage());
        }
    }

    private void logOrderAttempt(String tradeId, StrategySignalEvent signal, String userId) {
        UserOrderAudit audit = UserOrderAudit.builder()
                .userId(userId)
                .tenantId(UserContext.getCurrentUser().getTenantId())
                .tradeId(tradeId)
                .eventType("ORDER_ATTEMPT")
                .strategy(signal.getStrategy())
                .symbol(signal.getSymbol())
                .side(signal.getSide().toString())
                .price(BigDecimal.valueOf(signal.getPrice()))
                .createdAt(Instant.now())
                .build();

        auditRepository.save(audit);
    }

    private void logOrderSuccess(Order order, String userId) {
        UserOrderAudit audit = UserOrderAudit.builder()
                .userId(userId)
                .tenantId(UserContext.getCurrentUser().getTenantId())
                .tradeId(order.getId())
                .eventType("ORDER_SUCCESS")
                .orderId(order.getId())
                .symbol(order.getSymbol())
                .side(order.getSide().toString())
                .quantity(order.getQty())
                .price(BigDecimal.valueOf(order.getPrice()))
                .status(order.getStatus())
                .createdAt(Instant.now())
                .build();

        auditRepository.save(audit);
    }

    private void logOrderFailure(String userId, String reason) {
        UserOrderAudit audit = UserOrderAudit.builder()
                .userId(userId)
                .tenantId(UserContext.getCurrentUser().getTenantId())
                .tradeId(UUID.randomUUID().toString())
                .eventType("ORDER_FAILURE")
                .errorMessage(reason)
                .createdAt(Instant.now())
                .build();

        auditRepository.save(audit);
    }

    private String generateTradeId(String userId) {
        return userId + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record OrderExecutionContext(String brokerName, Order order,
                                         StrategySignalEvent signal, String userId, String tradeId) {}
}
