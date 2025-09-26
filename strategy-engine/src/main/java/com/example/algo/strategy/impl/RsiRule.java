package com.example.algo.strategy.impl;

import com.example.algo.common.events.*;
import com.example.algo.strategy.core.Strategy;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class RsiRule implements Strategy {
  public String name() {
    return "rsi";
  }

  public Optional<StrategySignalEvent> onTick(MarketTickEvent t) {
    return Optional.empty();
  }
}
