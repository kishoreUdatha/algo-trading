package com.example.algo.execution.service;

import com.example.algo.broker.core.*;
import com.example.algo.common.events.StrategySignalEvent;
import com.example.algo.common.model.*;
import com.example.algo.common.model.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExecutionService {
  private final BrokerRouter router;

  public Optional<String> routeFor(String symbol) {
    return Optional.of("upstox");
  }

  public Optional<Order> place(StrategySignalEvent s) {
    var brokerName = routeFor(s.getSymbol()).orElse("upstox");
    var client = router.byName(brokerName);
    var order =
        Order.builder()
            .symbol(s.getSymbol())
            .side(s.getSide())
            .qty(1)
            .type(OrderType.MARKET)
            .price(s.getPrice())
            .status("NEW")
            .build();
    return client.placeOrder(order).blockOptional();
  }
}
