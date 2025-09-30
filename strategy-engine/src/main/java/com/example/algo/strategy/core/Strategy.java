package com.example.algo.strategy.core;

import com.example.algo.common.events.*;
import com.example.algo.common.model.HistoricalContext;
import java.util.Optional;

public interface Strategy {
  String name();

  /**
   * Get historical data requirements for this strategy This defines what historical data needs to
   * be loaded
   */
  HistoricalDataRequirement getHistoricalDataRequirement();

  /**
   * Initialize strategy with historical data for a specific symbol This is called once per symbol
   * before processing real-time ticks
   */
  void initialize(String symbol, HistoricalContext historicalContext);

  /**
   * Process real-time market tick and potentially generate trading signal Strategy should already
   * be initialized with historical data
   */
  Optional<StrategySignalEvent> onTick(MarketTickEvent tick);

  /** Check if strategy is ready to process ticks for a symbol */
  boolean isInitialized(String symbol);

  /** Get strategy configuration parameters */
  StrategyConfig getConfig();

  /** Reset strategy state for a symbol (useful for testing or reinitialization) */
  void reset(String symbol);
}
