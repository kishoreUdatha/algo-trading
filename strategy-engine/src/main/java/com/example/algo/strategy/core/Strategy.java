package com.example.algo.strategy.core;

import com.example.algo.common.events.*;
import java.util.Optional;

public interface Strategy {
  String name();

  Optional<StrategySignalEvent> onTick(MarketTickEvent tick);
}
