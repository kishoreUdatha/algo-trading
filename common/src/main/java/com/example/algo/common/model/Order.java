package com.example.algo.common.model;

import com.example.algo.common.model.enums.*;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)  // ✅ enable toBuilder
public class Order {
    private String id;
    private String symbol;
    private OrderSide side;
    private int qty;
    private OrderType type;
    private double price;
    private String status;
}
