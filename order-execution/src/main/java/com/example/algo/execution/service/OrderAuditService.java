package com.example.algo.execution.service;

import com.example.algo.common.events.StrategySignalEvent;
import com.example.algo.common.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderAuditService {

    private final KafkaTemplate<String, Map<String, Object>> auditKafka;

    public void logOrderAttempt(String tradeId, StrategySignalEvent signal) {
        Map<String, Object> auditEvent = Map.of(
                "eventType", "ORDER_ATTEMPT",
                "tradeId", tradeId,
                "timestamp", Instant.now().toString(),
                "strategy", signal.getStrategy(),
                "symbol", signal.getSymbol(),
                "side", signal.getSide().toString(),
                "price", signal.getPrice()
        );

        publishAuditEvent(tradeId, auditEvent);
    }

    public void logOrderSuccess(String tradeId, Order order) {
        Map<String, Object> auditEvent = Map.of(
                "eventType", "ORDER_SUCCESS",
                "tradeId", tradeId,
                "timestamp", Instant.now().toString(),
                "orderId", order.getId(),
                "symbol", order.getSymbol(),
                "side", order.getSide().toString(),
                "quantity", order.getQty(),
                "price", order.getPrice(),
                "status", order.getStatus()
        );

        publishAuditEvent(tradeId, auditEvent);
    }

    public void logOrderFailure(String tradeId, String reason) {
        Map<String, Object> auditEvent = Map.of(
                "eventType", "ORDER_FAILURE",
                "tradeId", tradeId,
                "timestamp", Instant.now().toString(),
                "reason", reason
        );

        publishAuditEvent(tradeId, auditEvent);
    }

    private void publishAuditEvent(String tradeId, Map<String, Object> event) {
        try {
            auditKafka.send("tp.audit.orders", tradeId, event);
        } catch (Exception e) {
            log.error("Failed to publish audit event for trade {}: {}", tradeId, e.getMessage());
        }
    }
}