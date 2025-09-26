package com.example.algo.execution.messaging;

import com.example.algo.common.events.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderEventPublisher {
  private final KafkaTemplate<String, OrderLifecycleEvent> kafka;

  public void placed(StrategySignalEvent s) {
    kafka.send(
        "tp.orders.placed",
        s.getSymbol(),
        new OrderLifecycleEvent(
            null, "PLACED", s.getStrategy() + " " + s.getSide(), System.currentTimeMillis()));
  }

  public void executed(String orderId) {
    kafka.send(
        "tp.orders.executed",
        orderId,
        new OrderLifecycleEvent(orderId, "EXECUTED", "done", System.currentTimeMillis()));
  }

  public void blocked(StrategySignalEvent s, String reason) {
    kafka.send(
        "tp.risk.blocked",
        s.getSymbol(),
        new OrderLifecycleEvent(null, "BLOCKED", reason, System.currentTimeMillis()));
  }
}
