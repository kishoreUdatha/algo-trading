package com.example.algo.strategy.impl;

import com.example.algo.common.events.*;
import com.example.algo.common.model.Candle;
import com.example.algo.common.model.HistoricalContext;
import com.example.algo.common.model.enums.OrderSide;
import com.example.algo.strategy.core.HistoricalDataRequirement;
import com.example.algo.strategy.core.Strategy;
import com.example.algo.strategy.core.StrategyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class MovingAverageCrossover implements Strategy {

  // Strategy state per symbol
  private final Map<String, Deque<Double>> shortMA = new ConcurrentHashMap<>();
  private final Map<String, Deque<Double>> longMA = new ConcurrentHashMap<>();
  private final Map<String, Boolean> initialized = new ConcurrentHashMap<>();
  private final Map<String, Double> lastShortMA = new ConcurrentHashMap<>();
  private final Map<String, Double> lastLongMA = new ConcurrentHashMap<>();
  private final Map<String, LocalDateTime> lastSignalTime = new ConcurrentHashMap<>();

  // Strategy configuration
  private final StrategyConfig config;

  public MovingAverageCrossover() {
    this.config =
        StrategyConfig.builder()
            .strategyName("ma-crossover")
            .enabled(true)
            .maxPositionSize(10000.0)
            .stopLossPercentage(2.0)
            .takeProfitPercentage(5.0)
            .maxDailyLoss(1000.0)
            .maxSignalsPerDay(10)
            .cooldownMinutes(15)
            .parameters(createDefaultParameters())
            .build();
  }

  @Override
  public String name() {
    return "ma-crossover";
  }

  @Override
  public HistoricalDataRequirement getHistoricalDataRequirement() {
    // Get periods from configuration
    int shortPeriod = config.getIntParameter("shortPeriod", 50);
    int longPeriod = config.getIntParameter("longPeriod", 200);

    // Create requirements based on the longer period
    return HistoricalDataRequirement.forMovingAverage(longPeriod);
  }

  @Override
  public void initialize(String symbol, HistoricalContext historicalContext) {
    log.info("Initializing MovingAverageCrossover strategy for symbol: {}", symbol);

    try {
      // Get configuration parameters
      int shortPeriod = config.getIntParameter("shortPeriod", 50);
      int longPeriod = config.getIntParameter("longPeriod", 200);

      // Initialize data structures
      Deque<Double> shortQueue = new ArrayDeque<>();
      Deque<Double> longQueue = new ArrayDeque<>();

      // Use minute candles for initialization (most granular data)
      List<Candle> candles = historicalContext.getMinuteCandles();

      if (candles.isEmpty()) {
        // Fallback to hourly or daily candles
        candles = historicalContext.getHourlyCandles();
        if (candles.isEmpty()) {
          candles = historicalContext.getDailyCandles();
        }
      }

      if (!candles.isEmpty()) {
        // Pre-populate moving averages from historical data
        for (Candle candle : candles) {
          double price = candle.getClose();

          // Add to short MA queue
          shortQueue.addLast(price);
          if (shortQueue.size() > shortPeriod) {
            shortQueue.removeFirst();
          }

          // Add to long MA queue
          longQueue.addLast(price);
          if (longQueue.size() > longPeriod) {
            longQueue.removeFirst();
          }
        }

        // Store queues for this symbol
        shortMA.put(symbol, shortQueue);
        longMA.put(symbol, longQueue);

        // Calculate initial MA values
        if (shortQueue.size() >= shortPeriod) {
          lastShortMA.put(symbol, calculateAverage(shortQueue));
        }
        if (longQueue.size() >= longPeriod) {
          lastLongMA.put(symbol, calculateAverage(longQueue));
        }

        log.info(
            "Initialized {} with {} candles. Short MA: {}, Long MA: {}, Ready: {}",
            symbol,
            candles.size(),
            shortQueue.size(),
            longQueue.size(),
            isInitialized(symbol));
      } else {
        log.warn(
            "No historical data available for symbol: {}. Strategy will build state from live data.",
            symbol);
        shortMA.put(symbol, new ArrayDeque<>());
        longMA.put(symbol, new ArrayDeque<>());
      }

      initialized.put(symbol, true);

    } catch (Exception e) {
      log.error("Failed to initialize MovingAverageCrossover for symbol: {}", symbol, e);
      initialized.put(symbol, false);
    }
  }

  @Override
  public Optional<StrategySignalEvent> onTick(MarketTickEvent tick) {
    String symbol = tick.getSymbol();

    // Check if strategy is initialized for this symbol
    if (!isInitialized(symbol)) {
      log.debug("Strategy not initialized for symbol: {}", symbol);
      return Optional.empty();
    }

    try {
      // Get configuration parameters
      int shortPeriod = config.getIntParameter("shortPeriod", 50);
      int longPeriod = config.getIntParameter("longPeriod", 200);
      int cooldownMinutes = config.getCooldownMinutes();

      // Check cooldown period
      if (isInCooldown(symbol, cooldownMinutes)) {
        return Optional.empty();
      }

      // Get or create queues for this symbol
      Deque<Double> shortQueue = shortMA.computeIfAbsent(symbol, k -> new ArrayDeque<>());
      Deque<Double> longQueue = longMA.computeIfAbsent(symbol, k -> new ArrayDeque<>());

      // Add new price to queues
      double currentPrice = tick.getLtp();
      shortQueue.addLast(currentPrice);
      longQueue.addLast(currentPrice);

      // Maintain queue sizes
      if (shortQueue.size() > shortPeriod) {
        shortQueue.removeFirst();
      }
      if (longQueue.size() > longPeriod) {
        longQueue.removeFirst();
      }

      // Check if we have enough data points
      if (shortQueue.size() < shortPeriod || longQueue.size() < longPeriod) {
        log.debug(
            "Insufficient data for {}: short={}, long={}",
            symbol,
            shortQueue.size(),
            longQueue.size());
        return Optional.empty();
      }

      // Calculate current moving averages
      double currentShortMA = calculateAverage(shortQueue);
      double currentLongMA = calculateAverage(longQueue);

      // Get previous MA values
      Double prevShortMA = lastShortMA.get(symbol);
      Double prevLongMA = lastLongMA.get(symbol);

      // Update last MA values
      lastShortMA.put(symbol, currentShortMA);
      lastLongMA.put(symbol, currentLongMA);

      // Check for crossover signals only if we have previous values
      if (prevShortMA != null && prevLongMA != null) {
        Optional<StrategySignalEvent> signal =
            detectCrossover(
                symbol,
                currentPrice,
                tick.getTs(),
                prevShortMA,
                prevLongMA,
                currentShortMA,
                currentLongMA);

        if (signal.isPresent()) {
          lastSignalTime.put(symbol, LocalDateTime.now());
          log.info(
              "Generated {} signal for {} at price {}: Short MA {:.2f}, Long MA {:.2f}",
              signal.get().getSide(),
              symbol,
              currentPrice,
              currentShortMA,
              currentLongMA);
        }

        return signal;
      }

      return Optional.empty();

    } catch (Exception e) {
      log.error("Error processing tick for symbol: {}", symbol, e);
      return Optional.empty();
    }
  }

  @Override
  public boolean isInitialized(String symbol) {
    return initialized.getOrDefault(symbol, false)
        && shortMA.containsKey(symbol)
        && longMA.containsKey(symbol)
        && shortMA.get(symbol).size() >= config.getIntParameter("shortPeriod", 50)
        && longMA.get(symbol).size() >= config.getIntParameter("longPeriod", 200);
  }

  @Override
  public StrategyConfig getConfig() {
    return config;
  }

  @Override
  public void reset(String symbol) {
    log.info("Resetting MovingAverageCrossover strategy for symbol: {}", symbol);
    shortMA.remove(symbol);
    longMA.remove(symbol);
    initialized.remove(symbol);
    lastShortMA.remove(symbol);
    lastLongMA.remove(symbol);
    lastSignalTime.remove(symbol);
  }

  /** Detect crossover signals between short and long moving averages */
  private Optional<StrategySignalEvent> detectCrossover(
      String symbol,
      double currentPrice,
      long timestamp,
      double prevShortMA,
      double prevLongMA,
      double currentShortMA,
      double currentLongMA) {

    // Golden Cross: Short MA crosses above Long MA (Bullish signal)
    if (prevShortMA <= prevLongMA && currentShortMA > currentLongMA) {
      return Optional.of(
          StrategySignalEvent.builder()
              .strategy(name())
              .symbol(symbol)
              .side(OrderSide.BUY)
              .price(currentPrice)
              .ts(timestamp)
              .build());
    }

    // Death Cross: Short MA crosses below Long MA (Bearish signal)
    if (prevShortMA >= prevLongMA && currentShortMA < currentLongMA) {
      return Optional.of(
          StrategySignalEvent.builder()
              .strategy(name())
              .symbol(symbol)
              .side(OrderSide.SELL)
              .price(currentPrice)
              .ts(timestamp)
              .build());
    }

    return Optional.empty();
  }

  /** Check if strategy is in cooldown period for a symbol */
  private boolean isInCooldown(String symbol, int cooldownMinutes) {
    LocalDateTime lastSignal = lastSignalTime.get(symbol);
    if (lastSignal == null) {
      return false;
    }
    return LocalDateTime.now().isBefore(lastSignal.plusMinutes(cooldownMinutes));
  }

  /** Calculate simple moving average from deque */
  private double calculateAverage(Deque<Double> queue) {
    return queue.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
  }

  /** Create default configuration parameters */
  private Map<String, Object> createDefaultParameters() {
    Map<String, Object> params = new HashMap<>();
    params.put("shortPeriod", 50); // Short moving average period
    params.put("longPeriod", 200); // Long moving average period
    params.put("minVolume", 1000); // Minimum volume for signal
    params.put("priceThreshold", 0.5); // Minimum price change percentage
    return params;
  }
}
