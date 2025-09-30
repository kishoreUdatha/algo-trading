package com.example.algo.common.model;

import com.example.algo.common.model.enums.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class Order {
    // Order identification
    private String id;                    // Internal order ID
    private String brokerOrderId;         // Broker's order ID
    private String clientId;              // Client/User ID

    // Order details
    private String symbol;                // Trading symbol
    private OrderSide side;               // BUY/SELL
    private int quantity;                 // Number of shares/units
    private int filledQuantity;           // Executed quantity
    private int remainingQuantity;        // Pending quantity
    private OrderType type;               // MARKET/LIMIT/STOP_LOSS
    private double price;                 // Order price
    private double stopPrice;             // Stop price for stop orders
    private double limitPrice;            // Limit price for limit orders
    private double averagePrice;          // Average execution price

    // Order lifecycle
    private OrderStatus status;           // Order status enum
    private String statusMessage;         // Status description
    private LocalDateTime createdAt;      // Order creation time
    private LocalDateTime placedAt;       // Broker placement time
    private LocalDateTime filledAt;       // Execution completion time
    private LocalDateTime cancelledAt;    // Cancellation time
    private LocalDateTime updatedAt;      // Last update time

    // Strategy and risk management
    private String strategyName;          // Originating strategy
    private String signalId;              // Original signal ID
    private double stopLoss;              // Stop loss price
    private double takeProfit;            // Take profit price
    private double riskAmount;            // Maximum risk amount
    private double positionSize;          // Position size in currency

    // Execution details
    private String brokerName;            // Executing broker
    private double commission;            // Brokerage fees
    private double taxes;                 // Tax amount
    private double totalCost;             // Total order cost
    private String exchange;              // Exchange (NSE/BSE)
    private String product;               // Product type (CNC/MIS/NRML)
    private String validity;              // Order validity (DAY/IOC/GTT)

    // Audit and tracking
    private String rejectionReason;       // Rejection reason if failed
    private int retryCount;               // Number of retry attempts
    private String parentOrderId;         // Parent order for SL/TP orders
    private String orderSource;           // ALGO/MANUAL/API
    private String tags;                  // Additional tags

    // Helper methods
    public boolean isCompleted() {
        return status == OrderStatus.FILLED || status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED;
    }

    public boolean isActive() {
        return status == OrderStatus.PLACED || status == OrderStatus.PARTIALLY_FILLED;
    }

    public boolean canBeCancelled() {
        return status == OrderStatus.PLACED || status == OrderStatus.PARTIALLY_FILLED;
    }

    public double getExecutedValue() {
        return filledQuantity * averagePrice;
    }

    public double getTotalCostWithFees() {
        return getExecutedValue() + commission + taxes;
    }

    public double getPnL() {
        if (side == OrderSide.BUY) {
            return 0; // PnL calculated when sold
        } else {
            return getExecutedValue() - totalCost; // For SELL orders
        }
    }

    // Backward compatibility
    @Deprecated
    public int getQty() {
        return quantity;
    }

    @Deprecated
    public void setQty(int qty) {
        this.quantity = qty;
    }

    @Deprecated
    public String getStatus() {
        return status != null ? status.name() : "UNKNOWN";
    }

    @Deprecated
    public void setStatus(String status) {
        try {
            this.status = OrderStatus.valueOf(status.toUpperCase());
        } catch (Exception e) {
            this.status = OrderStatus.UNKNOWN;
        }
    }
}
