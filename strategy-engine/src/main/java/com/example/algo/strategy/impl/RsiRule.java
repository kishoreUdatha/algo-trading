package com.example.algo.strategy.impl;

import com.example.algo.common.events.*;
import com.example.algo.common.indicators.TechnicalIndicators;
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
public class RsiRule implements Strategy {

  // Strategy state per symbol
  private final Map<String, List<Double>> priceHistory = new ConcurrentHashMap<>();
  private final Map<String, Boolean> initialized = new ConcurrentHashMap<>();
  private final Map<String, Double> lastRSI = new ConcurrentHashMap<>();
  private final Map<String, OrderSide> lastSignal = new ConcurrentHashMap<>();
  private final Map<String, LocalDateTime> lastSignalTime = new ConcurrentHashMap<>();

  // Strategy configuration
  private final StrategyConfig config;

  public RsiRule() {
    this.config =
        StrategyConfig.builder()
            .strategyName("rsi")
            .enabled(true)
            .maxPositionSize(5000.0)
            .stopLossPercentage(3.0)
            .takeProfitPercentage(6.0)
            .maxDailyLoss(500.0)
            .maxSignalsPerDay(8)
            .cooldownMinutes(30)
            .parameters(createDefaultParameters())
            .build();
  }

  @Override
  public String name() {
    return "rsi";
  }

  @Override
  public HistoricalDataRequirement getHistoricalDataRequirement() {
    // Get RSI period from configuration
    int rsiPeriod = config.getIntParameter("rsiPeriod", 14);
    String primaryTimeframe = config.getStringParameter("primaryTimeframe", "1h");

    // Create requirements based on RSI period and timeframe
    return HistoricalDataRequirement.forRSI(rsiPeriod, primaryTimeframe);
  }

  @Override
  public void initialize(String symbol, HistoricalContext historicalContext) {
    log.info("Initializing RSI strategy for symbol: {}", symbol);

    try {
      int rsiPeriod = config.getIntParameter("rsiPeriod", 14);
      int maxHistorySize = config.getIntParameter("maxHistorySize", 500);

      // Initialize price history list
      List<Double> prices = new ArrayList<>();

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
        // Extract closing prices from historical candles
        for (Candle candle : candles) {
          prices.add(candle.getClose());
        }

        // Keep only the most recent prices to avoid memory issues
        if (prices.size() > maxHistorySize) {
          prices = prices.subList(prices.size() - maxHistorySize, prices.size());
        }

        // Store price history for this symbol
        priceHistory.put(symbol, new ArrayList<>(prices));

        // Calculate initial RSI if we have enough data
        if (prices.size() >= rsiPeriod + 1) {
          double initialRSI = TechnicalIndicators.calculateRSI(prices, rsiPeriod);
          lastRSI.put(symbol, initialRSI);
          log.info(
              "Initialized {} with {} prices. Initial RSI: {:.2f}, Ready: {}",
              symbol,
              prices.size(),
              initialRSI,
              isInitialized(symbol));
        } else {
          log.warn(
              "Insufficient historical data for RSI calculation on {}: {} prices, need {}",
              symbol,
              prices.size(),
              rsiPeriod + 1);
        }
      } else {
        log.warn(
            "No historical data available for symbol: {}. Strategy will build state from live data.",
            symbol);
        priceHistory.put(symbol, new ArrayList<>());
      }

      initialized.put(symbol, true);

    } catch (Exception e) {
      log.error("Failed to initialize RSI strategy for symbol: {}", symbol, e);
      initialized.put(symbol, false);
    }
  }

  @Override
  public Optional<StrategySignalEvent> onTick(MarketTickEvent tick) {
    String symbol = tick.getSymbol();

    // Check if strategy is initialized for this symbol
    if (!initialized.getOrDefault(symbol, false)) {
      log.debug("RSI strategy not initialized for symbol: {}", symbol);
      return Optional.empty();
    }

    try {
      // Get configuration parameters
      int rsiPeriod = config.getIntParameter("rsiPeriod", 14);
      double oversoldThreshold = config.getDoubleParameter("oversoldThreshold", 30.0);
      double overboughtThreshold = config.getDoubleParameter("overboughtThreshold", 70.0);
      double neutralZoneThreshold = config.getDoubleParameter("neutralZoneThreshold", 5.0);
      int cooldownMinutes = config.getCooldownMinutes();
      int maxHistorySize = config.getIntParameter("maxHistorySize", 500);

      // Check cooldown period
      if (isInCooldown(symbol, cooldownMinutes)) {
        return Optional.empty();
      }

      // Get or create price history for this symbol
      List<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayList<>());

      // Add new price to history
      double currentPrice = tick.getLtp();
      prices.add(currentPrice);

      // Maintain history size to prevent memory issues
      if (prices.size() > maxHistorySize) {
        prices.remove(0);
      }

      // Check if we have enough data for RSI calculation
      if (prices.size() < rsiPeriod + 1) {
        log.debug(
            "Insufficient price history for RSI calculation on {}: {} prices, need {}",
            symbol,
            prices.size(),
            rsiPeriod + 1);
        return Optional.empty();
      }

      // Calculate current RSI
      double currentRSI = TechnicalIndicators.calculateRSI(prices, rsiPeriod);
      Double prevRSI = lastRSI.get(symbol);

      // Update last RSI value
      lastRSI.put(symbol, currentRSI);

      // Generate signals based on RSI levels and transitions
      Optional<StrategySignalEvent> signal =
          generateRSISignal(
              symbol,
              currentPrice,
              tick.getTs(),
              currentRSI,
              prevRSI,
              oversoldThreshold,
              overboughtThreshold,
              neutralZoneThreshold);

      if (signal.isPresent()) {
        lastSignal.put(symbol, signal.get().getSide());
        lastSignalTime.put(symbol, LocalDateTime.now());
        log.info(
            "Generated {} signal for {} at price {}: RSI {:.2f} (prev: {:.2f})",
            signal.get().getSide(),
            symbol,
            currentPrice,
            currentRSI,
            prevRSI != null ? prevRSI : 0.0);
      }

      return signal;

    } catch (Exception e) {
      log.error("Error processing RSI tick for symbol: {}", symbol, e);
      return Optional.empty();
    }
  }

  @Override
  public boolean isInitialized(String symbol) {
    return initialized.getOrDefault(symbol, false)
        && priceHistory.containsKey(symbol)
        && priceHistory.get(symbol).size() >= config.getIntParameter("rsiPeriod", 14) + 1;
  }

  @Override
  public StrategyConfig getConfig() {
    return config;
  }

  @Override
  public void reset(String symbol) {
    log.info("Resetting RSI strategy for symbol: {}", symbol);
    priceHistory.remove(symbol);
    initialized.remove(symbol);
    lastRSI.remove(symbol);
    lastSignal.remove(symbol);
    lastSignalTime.remove(symbol);
  }

  /** Generate RSI-based trading signals */
  private Optional<StrategySignalEvent> generateRSISignal(
      String symbol,
      double currentPrice,
      long timestamp,
      double currentRSI,
      Double prevRSI,
      double oversoldThreshold,
      double overboughtThreshold,
      double neutralZoneThreshold) {

    OrderSide previousSignal = lastSignal.get(symbol);

    // RSI Oversold condition (potential BUY signal)
    if (currentRSI <= oversoldThreshold) {
      // Only generate BUY signal if:
      // 1. We haven't already signaled BUY recently
      // 2. RSI is moving up from extreme oversold (if we have previous RSI)
      boolean shouldBuy =
          previousSignal != OrderSide.BUY && (prevRSI == null || currentRSI >= prevRSI);

      if (shouldBuy) {
        return Optional.of(
            StrategySignalEvent.builder()
                .strategy(name())
                .symbol(symbol)
                .side(OrderSide.BUY)
                .price(currentPrice)
                .ts(timestamp)
                .build());
      }
    }

    // RSI Overbought condition (potential SELL signal)
    else if (currentRSI >= overboughtThreshold) {
      // Only generate SELL signal if:
      // 1. We haven't already signaled SELL recently
      // 2. RSI is moving down from extreme overbought (if we have previous RSI)
      boolean shouldSell =
          previousSignal != OrderSide.SELL && (prevRSI == null || currentRSI <= prevRSI);

      if (shouldSell) {
        return Optional.of(
            StrategySignalEvent.builder()
                .strategy(name())
                .symbol(symbol)
                .side(OrderSide.SELL)
                .price(currentPrice)
                .ts(timestamp)
                .build());
      }
    }

    // RSI returning to neutral zone (potential exit signal)
    else if (prevRSI != null
        && isReturnToNeutral(
            prevRSI, currentRSI, oversoldThreshold, overboughtThreshold, neutralZoneThreshold)) {
      // Generate opposite signal when RSI returns to neutral from extreme
      if (prevRSI <= oversoldThreshold && previousSignal == OrderSide.BUY) {
        return Optional.of(
            StrategySignalEvent.builder()
                .strategy(name())
                .symbol(symbol)
                .side(OrderSide.SELL)
                .price(currentPrice)
                .ts(timestamp)
                .build());
      } else if (prevRSI >= overboughtThreshold && previousSignal == OrderSide.SELL) {
        return Optional.of(
            StrategySignalEvent.builder()
                .strategy(name())
                .symbol(symbol)
                .side(OrderSide.BUY)
                .price(currentPrice)
                .ts(timestamp)
                .build());
      }
    }

    return Optional.empty();
  }

  /** Check if RSI is returning to neutral zone from extreme levels */
  private boolean isReturnToNeutral(
      double prevRSI,
      double currentRSI,
      double oversoldThreshold,
      double overboughtThreshold,
      double neutralZoneThreshold) {

    double neutralMid = 50.0;
    double neutralLower = neutralMid - neutralZoneThreshold;
    double neutralUpper = neutralMid + neutralZoneThreshold;

    // Return to neutral from oversold
    if (prevRSI <= oversoldThreshold && currentRSI >= neutralLower && currentRSI <= neutralUpper) {
      return true;
    }

    // Return to neutral from overbought
    if (prevRSI >= overboughtThreshold
        && currentRSI >= neutralLower
        && currentRSI <= neutralUpper) {
      return true;
    }

    return false;
  }

  /** Check if strategy is in cooldown period for a symbol */
  private boolean isInCooldown(String symbol, int cooldownMinutes) {
    LocalDateTime lastSignal = lastSignalTime.get(symbol);
    if (lastSignal == null) {
      return false;
    }
    return LocalDateTime.now().isBefore(lastSignal.plusMinutes(cooldownMinutes));
  }

  /** Create default configuration parameters */
  private Map<String, Object> createDefaultParameters() {
    Map<String, Object> params = new HashMap<>();
    params.put("rsiPeriod", 14); // RSI calculation period
    params.put("oversoldThreshold", 30.0); // RSI oversold level
    params.put("overboughtThreshold", 70.0); // RSI overbought level
    params.put("neutralZoneThreshold", 5.0); // Neutral zone around 50
    params.put("maxHistorySize", 500); // Maximum price history to keep
    params.put("minVolume", 500); // Minimum volume for signal
    params.put("primaryTimeframe", "1h"); // Primary timeframe for RSI calculation
    return params;
  }
}
