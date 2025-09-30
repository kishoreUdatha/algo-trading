package com.example.algo.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a real-time tick event (quote update) from broker/exchange.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TickEvent {

    private String symbol;      // e.g. "RELIANCE"
    private double price;       // Last traded price (LTP)
    private long volume;        // Volume at this tick (optional, can be 0)
    private long ts;            // Timestamp in millis (epoch)

    // Optional fields for richer ticks
    private double bid;         // Best bid price
    private double ask;         // Best ask price
    private long bidQty;        // Bid quantity
    private long askQty;        // Ask quantity
    private long timestamp;
    private String change;
}
