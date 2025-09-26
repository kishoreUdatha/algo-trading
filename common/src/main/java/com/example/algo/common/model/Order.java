package com.example.algo.common.model;

import com.example.algo.common.model.enums.*;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Order {
  String id;
  String symbol;
  OrderSide side;
  int qty;
  OrderType type;
  double price;
  String status;
}
