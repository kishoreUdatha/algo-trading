package com.example.algo.common.events;

import com.example.algo.common.model.enums.OrderSide;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StrategySignalEvent {
  String strategy;
  String symbol;
  OrderSide side;
  double price;
  long ts;
  String userId;
  String clientId;
}
