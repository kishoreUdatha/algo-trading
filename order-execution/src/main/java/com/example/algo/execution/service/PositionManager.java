package com.example.algo.execution.service;

import com.example.algo.common.model.enums.OrderSide;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionManager {

  private final RedisTemplate<String, String> redis;

  public void updatePosition(String symbol, OrderSide side, int quantity, double price) {
    String positionKey = "position:" + symbol;
    String valueKey = "position_value:" + symbol;

    try {
      // Update quantity
      long currentQty =
          redis.opsForValue().increment(positionKey, side == OrderSide.BUY ? quantity : -quantity);

      // Update value
      double currentValue =
          Double.parseDouble(
              redis.opsForValue().get(valueKey) != null
                  ? redis.opsForValue().get(valueKey)
                  : "0.0");

      double adjustment = (side == OrderSide.BUY ? 1 : -1) * quantity * price;
      redis.opsForValue().set(valueKey, String.valueOf(currentValue + adjustment));

      // Set expiry
      redis.expire(positionKey, Duration.ofDays(1));
      redis.expire(valueKey, Duration.ofDays(1));

      log.info(
          "Position updated for {}: {} shares, value: {}",
          symbol,
          currentQty,
          currentValue + adjustment);

    } catch (Exception e) {
      log.error("Failed to update position for {}: {}", symbol, e.getMessage());
    }
  }

  public Map<String, Object> getPosition(String symbol) {
    try {
      String positionKey = "position:" + symbol;
      String valueKey = "position_value:" + symbol;

      long quantity =
          Long.parseLong(
              redis.opsForValue().get(positionKey) != null
                  ? redis.opsForValue().get(positionKey)
                  : "0");

      double value =
          Double.parseDouble(
              redis.opsForValue().get(valueKey) != null
                  ? redis.opsForValue().get(valueKey)
                  : "0.0");

      Map<String, Object> position = new HashMap<>();
      position.put("symbol", symbol);
      position.put("quantity", quantity);
      position.put("value", value);
      position.put("averagePrice", quantity != 0 ? value / quantity : 0.0);

      return position;

    } catch (Exception e) {
      log.error("Failed to get position for {}: {}", symbol, e.getMessage());
      return Map.of("symbol", symbol, "quantity", 0, "value", 0.0, "averagePrice", 0.0);
    }
  }

  public double getTotalPositionValue() {
    // This is a simplified implementation
    // In production, you'd want to iterate through all positions
    return 0.0;
  }
}
