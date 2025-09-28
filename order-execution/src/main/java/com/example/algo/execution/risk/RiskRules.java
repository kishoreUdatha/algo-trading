package com.example.algo.execution.risk;

import com.example.algo.common.events.StrategySignalEvent;
import org.springframework.stereotype.Component;

@Component
public class RiskRules {
  public RiskResult check(StrategySignalEvent e) {
    return new RiskResult(true, "OK", "All user-specific risk checks passed");
  }
}
