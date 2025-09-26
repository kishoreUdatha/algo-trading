package com.example.algo.strategy.impl;

import com.example.algo.common.events.*;
import com.example.algo.common.model.enums.OrderSide;
import com.example.algo.strategy.core.Strategy;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MovingAverageCrossover implements Strategy {
  private final Map<String, Deque<Double>> sWin = new ConcurrentHashMap<>();
  private final Map<String, Deque<Double>> lWin = new ConcurrentHashMap<>();

  private static double avg(Deque<Double> q) {
    return q.stream().mapToDouble(d -> d).average().orElse(Double.NaN);
  }

  public String name() {
    return "ma-crossover";
  }

  public Optional<StrategySignalEvent> onTick(MarketTickEvent t) {
    sWin.computeIfAbsent(t.getSymbol(), k -> new ArrayDeque<>());
    lWin.computeIfAbsent(t.getSymbol(), k -> new ArrayDeque<>());
    var s = sWin.get(t.getSymbol());
    var l = lWin.get(t.getSymbol());
    s.addLast(t.getLtp());
    if (s.size() > 50) s.removeFirst();
    l.addLast(t.getLtp());
    if (l.size() > 200) l.removeFirst();
    if (s.size() >= 50 && l.size() >= 200) {
      double sa = avg(s), la = avg(l);
      if (sa > la)
        return Optional.of(
            new StrategySignalEvent(name(), t.getSymbol(), OrderSide.BUY, t.getLtp(), t.getTs()));
      if (sa < la)
        return Optional.of(
            new StrategySignalEvent(name(), t.getSymbol(), OrderSide.SELL, t.getLtp(), t.getTs()));
    }
    return Optional.empty();
  }
}
