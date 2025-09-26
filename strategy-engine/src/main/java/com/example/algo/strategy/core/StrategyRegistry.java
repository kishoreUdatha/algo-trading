package com.example.algo.strategy.core;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class StrategyRegistry {
  private final Map<String, Strategy> map = new HashMap<>();

  public void register(Strategy s) {
    map.put(s.name(), s);
  }

  public Collection<Strategy> all() {
    return map.values();
  }
}
