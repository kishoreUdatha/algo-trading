package com.example.algo.execution.messaging;

import com.example.algo.common.events.*;
import com.example.algo.execution.risk.*;
import com.example.algo.execution.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SignalConsumer {
  private final RiskRules risk;
  private final ExecutionService exec;
  private final OrderEventPublisher pub;

  @KafkaListener(topics = "tp.strategy.signals", groupId = "order-exec")
  public void onSignal(StrategySignalEvent e) {
    var rr = risk.check(e);
    if (rr.ok()) exec.place(e).ifPresent(o -> pub.placed(e));
    else pub.blocked(e, rr.reason());
  }
}
