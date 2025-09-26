package com.example.algo.strategy.messaging;

import com.example.algo.common.events.*;
import com.example.algo.strategy.core.*;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TickConsumer {
  private final StrategyRegistry registry;
  private final SignalPublisher publisher;

  @KafkaListener(topics = "tp.market.ticks", groupId = "strategy-engine")
  public void onTick(MarketTickEvent e) {
    registry.all().forEach(s -> s.onTick(e).ifPresent(publisher::publish));
  }
}
