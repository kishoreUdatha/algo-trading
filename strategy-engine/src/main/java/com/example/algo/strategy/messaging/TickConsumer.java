package com.example.algo.strategy.messaging;

import com.example.algo.common.events.*;
import com.example.algo.strategy.core.*;
import com.example.algo.strategy.service.StrategyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class TickConsumer {

  private final StrategyRegistry registry;
  private final SignalPublisher publisher;
  private final StrategyManager strategyManager;

  // Performance metrics
  private final AtomicLong totalTicksProcessed = new AtomicLong(0);
  private final AtomicLong totalSignalsGenerated = new AtomicLong(0);
  private final AtomicLong totalProcessingErrors = new AtomicLong(0);

  @KafkaListener(topics = "tp.market.ticks", groupId = "strategy-engine")
  public void onTick(MarketTickEvent event) {
    try {
      totalTicksProcessed.incrementAndGet();

      String symbol = event.getSymbol();

      // Check if strategies are initialized for this symbol
      if (!strategyManager.isSymbolInitialized(symbol)) {
        log.debug(
            "Strategies not yet initialized for symbol: {}. Skipping tick processing.", symbol);
        return;
      }

      // Process tick through all initialized strategies
      registry
          .all()
          .forEach(
              strategy -> {
                try {
                  // Only process if strategy is enabled and initialized for this symbol
                  if (strategy.getConfig().isEnabled() && strategy.isInitialized(symbol)) {
                    strategy
                        .onTick(event)
                        .ifPresent(
                            signal -> {
                              try {
                                publisher.publish(signal);
                                totalSignalsGenerated.incrementAndGet();
                                log.debug(
                                    "Published signal from strategy '{}' for symbol '{}': {}",
                                    strategy.name(),
                                    symbol,
                                    signal.getSide());
                              } catch (Exception e) {
                                log.error(
                                    "Failed to publish signal from strategy '{}' for symbol '{}'",
                                    strategy.name(),
                                    symbol,
                                    e);
                                totalProcessingErrors.incrementAndGet();
                              }
                            });
                  }
                } catch (Exception e) {
                  log.error(
                      "Error processing tick in strategy '{}' for symbol '{}': {}",
                      strategy.name(),
                      symbol,
                      e.getMessage(),
                      e);
                  totalProcessingErrors.incrementAndGet();
                }
              });

      // Log performance metrics periodically
      long totalTicks = totalTicksProcessed.get();
      if (totalTicks % 1000 == 0) {
        log.info(
            "Tick processing metrics - Total: {}, Signals: {}, Errors: {}",
            totalTicks,
            totalSignalsGenerated.get(),
            totalProcessingErrors.get());
      }

    } catch (Exception e) {
      log.error("Critical error processing market tick for symbol: {}", event.getSymbol(), e);
      totalProcessingErrors.incrementAndGet();
    }
  }

  /** Get processing statistics */
  public TickProcessingStats getProcessingStats() {
    return TickProcessingStats.builder()
        .totalTicksProcessed(totalTicksProcessed.get())
        .totalSignalsGenerated(totalSignalsGenerated.get())
        .totalProcessingErrors(totalProcessingErrors.get())
        .errorRate(calculateErrorRate())
        .signalGenerationRate(calculateSignalRate())
        .build();
  }

  private double calculateErrorRate() {
    long total = totalTicksProcessed.get();
    return total > 0 ? (double) totalProcessingErrors.get() / total : 0.0;
  }

  private double calculateSignalRate() {
    long total = totalTicksProcessed.get();
    return total > 0 ? (double) totalSignalsGenerated.get() / total : 0.0;
  }

  /** Reset processing statistics */
  public void resetStats() {
    totalTicksProcessed.set(0);
    totalSignalsGenerated.set(0);
    totalProcessingErrors.set(0);
    log.info("Tick processing statistics reset");
  }
}
