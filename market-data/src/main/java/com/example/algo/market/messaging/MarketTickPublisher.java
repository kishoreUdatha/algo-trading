package com.example.algo.market.messaging;

import com.example.algo.common.events.MarketTickEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketTickPublisher {
  private final KafkaTemplate<String, MarketTickEvent> kafka;

  public void publish(MarketTickEvent e) {

      kafka.send("tp.market.ticks", e.getSymbol(), e);
  }
}
