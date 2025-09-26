package com.example.algo.strategy.messaging;

import com.example.algo.common.events.StrategySignalEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SignalPublisher {
  private final KafkaTemplate<String, StrategySignalEvent> kafka;

  public void publish(StrategySignalEvent e) {
    kafka.send("tp.strategy.signals", e.getSymbol(), e);
  }
}
