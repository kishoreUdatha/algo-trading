package com.example.algo.execution.service;

import com.example.algo.broker.core.BrokerRouter;
import com.example.algo.common.events.StrategySignalEvent;
import com.example.algo.common.exception.BrokerException;
import com.example.algo.common.model.Order;
import com.example.algo.common.model.enums.OrderSide;
import com.example.algo.common.model.enums.OrderType;
import com.example.algo.execution.risk.EnhancedRiskRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedExecutionService {

    private final BrokerRouter router;
    private final EnhancedRiskRules riskRules;
    private final OrderAuditService auditService;
    private final PositionManager positionManager;

    @Transactional
    public Mono<Optional<Order>> placeOrder(StrategySignalEvent signal) {
        String tradeId = UUID.randomUUID().toString();

        return Mono.fromCallable(() -> {
                    // Log order attempt
                    auditService.logOrderAttempt(tradeId, signal);

                    // Determine broker and build order
                    String brokerName = determineBroker(signal.getSymbol());
                    Order order = buildOrder(signal, tradeId);

                    return new OrderContext(brokerName, order, signal);
                })
                .flatMap(this::executeOrder)
                .doOnSuccess(result -> {
                    if (result.isPresent()) {
                        Order order = result.get();
                        // Update position tracking
                        riskRules.updatePosition(order.getSymbol(), order.getSide(),
                                order.getPrice() * order.getQty());

                        // Log successful order
                        auditService.logOrderSuccess(tradeId, order);
                        log.info("Order placed successfully: {} for signal from {}",
                                order.getId(), signal.getStrategy());
                    } else {
                        auditService.logOrderFailure(tradeId, "Order execution returned empty");
                    }
                })
                .doOnError(error -> {
                    auditService.logOrderFailure(tradeId, error.getMessage());
                    log.error("Order placement failed for signal from {}: {}",
                            signal.getStrategy(), error.getMessage());
                })
                .onErrorReturn(Optional.empty());
    }

    private Mono<Optional<Order>> executeOrder(OrderContext context) {
        try {
            var client = router.byName(context.brokerName());

            return client.placeOrder(context.order())
                    .map(Optional::of)
                    .onErrorResume(BrokerException.class, ex -> {
                        log.error("Broker error placing order: {}", ex.getMessage());
                        return Mono.just(Optional.empty());
                    })
                    .onErrorResume(Exception.class, ex -> {
                        log.error("Unexpected error placing order: {}", ex.getMessage());
                        return Mono.just(Optional.empty());
                    });

        } catch (Exception e) {
            log.error("Failed to get broker client: {}", e.getMessage());
            return Mono.just(Optional.empty());
        }
    }

    private String determineBroker(String symbol) {
        // Simple routing logic - enhance as needed
        if (symbol.startsWith("NIFTY")) {
            return "zerodha";
        }
        return "upstox";
    }

    private Order buildOrder(StrategySignalEvent signal, String clientOrderId) {
        return Order.builder()
                .id(clientOrderId)
                .symbol(signal.getSymbol())
                .side(signal.getSide())
                .qty(calculateQuantity(signal))
                .type(determineOrderType(signal))
                .price(signal.getPrice())
                .status("NEW")
                .build();
    }

    private int calculateQuantity(StrategySignalEvent signal) {
        // Implement position sizing logic
        return 1; // Default quantity for now
    }

    private OrderType determineOrderType(StrategySignalEvent signal) {
        // For now, always use market orders
        // In production, this might depend on strategy or market conditions
        return OrderType.MARKET;
    }

    private record OrderContext(String brokerName, Order order, StrategySignalEvent signal) {}
}