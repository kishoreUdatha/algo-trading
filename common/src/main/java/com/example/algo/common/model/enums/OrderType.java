package com.example.algo.common.model.enums;

public enum OrderType {
    // Basic order types
    MARKET,                  // Execute immediately at current market price
    LIMIT,                   // Execute only at specified price or better

    // Stop orders
    STOP_LOSS,              // Stop-loss limit order (trigger + limit price)
    STOP_LOSS_MARKET,       // Stop-loss market order (trigger price only)

    // Advanced order types
    STOP_LIMIT,             // Stop order that becomes limit order when triggered
    TRAILING_STOP,          // Dynamic stop that trails the market price
    BRACKET,                // Parent order with automatic SL and TP orders
    COVER,                  // Order with mandatory stop-loss

    // Time-based orders
    IOC,                    // Immediate or Cancel
    FOK,                    // Fill or Kill
    GTT,                    // Good Till Triggered
    GTC,                    // Good Till Cancelled

    // Special algo orders
    ICEBERG,                // Large order split into smaller chunks
    TWAP,                   // Time Weighted Average Price
    VWAP,                   // Volume Weighted Average Price
    IMPLEMENTATION_SHORTFALL // Minimize market impact

    // Note: Some brokers may not support all order types
    // The system will map to supported types during order placement
}
