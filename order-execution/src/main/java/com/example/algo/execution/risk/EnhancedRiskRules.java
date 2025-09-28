package com.example.algo.execution.risk;

import com.example.algo.common.events.StrategySignalEvent;
import com.example.algo.common.exception.RiskManagementException;
import com.example.algo.common.model.enums.OrderSide;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class EnhancedRiskRules {

    private final RedisTemplate<String, String> redis;

    @Value("${risk.max-order-value:100000}")
    private double maxOrderValue;

    @Value("${risk.max-orders-per-minute:10}")
    private int maxOrdersPerMinute;

    @Value("${risk.max-daily-loss:50000}")
    private double maxDailyLoss;

    @Value("${risk.position-limit:1000000}")
    private double positionLimit;

    // In-memory counters for rate limiting
    private final ConcurrentHashMap<String, AtomicInteger> orderCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> lastOrderTime = new ConcurrentHashMap<>();

    public RiskResult check(StrategySignalEvent event) {
        try {
            // 1. Order value check
            double orderValue = event.getPrice() * getQuantity(event);
            if (orderValue > maxOrderValue) {
                return new RiskResult(false, "ORDER_VALUE_EXCEEDED",
                        String.format("Order value %.2f exceeds limit %.2f", orderValue, maxOrderValue));
            }

            // 2. Rate limiting check
            RiskResult rateCheck = checkRateLimit(event);
            if (!rateCheck.ok()) {
                return rateCheck;
            }

            // 3. Daily loss check
            RiskResult dailyLossCheck = checkDailyLoss(event);
            if (!dailyLossCheck.ok()) {
                return dailyLossCheck;
            }

            // 4. Position limit check
            RiskResult positionCheck = checkPositionLimit(event);
            if (!positionCheck.ok()) {
                return positionCheck;
            }

            // 5. Market hours check
            RiskResult marketHoursCheck = checkMarketHours();
            if (!marketHoursCheck.ok()) {
                return marketHoursCheck;
            }

            log.info("Risk check passed for {} {} @ {}",
                    event.getSymbol(), event.getSide(), event.getPrice());

            return new RiskResult(true, "OK", "All risk checks passed");

        } catch (Exception e) {
            log.error("Risk check failed due to error", e);
            return new RiskResult(false, "RISK_CHECK_ERROR", "Risk validation failed");
        }
    }

    private RiskResult checkRateLimit(StrategySignalEvent event) {
        String key = "rate_limit:" + event.getSymbol();
        String countKey = key + ":count";
        String timeKey = key + ":time";

        long currentTime = System.currentTimeMillis();
        long lastTime = Long.parseLong(redis.opsForValue().get(timeKey) != null ?
                redis.opsForValue().get(timeKey) : "0");

        // Reset counter if more than a minute has passed
        if (currentTime - lastTime > 60000) {
            redis.opsForValue().set(countKey, "0");
            redis.opsForValue().set(timeKey, String.valueOf(currentTime));
        }

        int currentCount = Integer.parseInt(redis.opsForValue().get(countKey) != null ?
                redis.opsForValue().get(countKey) : "0");

        if (currentCount >= maxOrdersPerMinute) {
            return new RiskResult(false, "RATE_LIMIT_EXCEEDED",
                    String.format("Order rate limit exceeded: %d orders in last minute", currentCount));
        }

        // Increment counter
        redis.opsForValue().increment(countKey);
        redis.expire(countKey, Duration.ofMinutes(2));
        redis.expire(timeKey, Duration.ofMinutes(2));

        return new RiskResult(true, "OK", "Rate limit check passed");
    }

    private RiskResult checkDailyLoss(StrategySignalEvent event) {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String lossKey = "daily_loss:" + today;

        double currentLoss = Double.parseDouble(redis.opsForValue().get(lossKey) != null ?
                redis.opsForValue().get(lossKey) : "0.0");

        if (Math.abs(currentLoss) > maxDailyLoss) {
            return new RiskResult(false, "DAILY_LOSS_EXCEEDED",
                    String.format("Daily loss limit exceeded: %.2f", currentLoss));
        }

        return new RiskResult(true, "OK", "Daily loss check passed");
    }

    private RiskResult checkPositionLimit(StrategySignalEvent event) {
        String positionKey = "position:" + event.getSymbol();
        double currentPosition = Double.parseDouble(redis.opsForValue().get(positionKey) != null ?
                redis.opsForValue().get(positionKey) : "0.0");

        double orderValue = event.getPrice() * getQuantity(event);
        double newPosition = event.getSide() == OrderSide.BUY ?
                currentPosition + orderValue : currentPosition - orderValue;

        if (Math.abs(newPosition) > positionLimit) {
            return new RiskResult(false, "POSITION_LIMIT_EXCEEDED",
                    String.format("Position limit exceeded: %.2f > %.2f",
                            Math.abs(newPosition), positionLimit));
        }

        return new RiskResult(true, "OK", "Position limit check passed");
    }

    private RiskResult checkMarketHours() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        int minute = now.getMinute();

        // Indian market hours: 9:15 AM to 3:30 PM
        boolean isMarketOpen = (hour > 9 || (hour == 9 && minute >= 15)) &&
                (hour < 15 || (hour == 15 && minute <= 30));

        if (!isMarketOpen) {
            return new RiskResult(false, "MARKET_CLOSED",
                    "Market is closed. Trading not allowed.");
        }

        return new RiskResult(true, "OK", "Market hours check passed");
    }

    private int getQuantity(StrategySignalEvent event) {
        // For now, return a default quantity. This should be calculated based on strategy
        return 1;
    }

    public void updatePosition(String symbol, OrderSide side, double value) {
        String positionKey = "position:" + symbol;
        double adjustment = side == OrderSide.BUY ? value : -value;
        redis.opsForValue().increment(positionKey, adjustment);
        redis.expire(positionKey, Duration.ofDays(1));
    }

    public void updateDailyPnL(double pnl) {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String lossKey = "daily_loss:" + today;
        redis.opsForValue().increment(lossKey, -pnl); // Negative PnL increases loss
        redis.expire(lossKey, Duration.ofDays(2));
    }

}