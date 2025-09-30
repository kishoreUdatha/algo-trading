package com.example.algo.execution.service;

import com.example.algo.broker.core.*;
import com.example.algo.common.events.StrategySignalEvent;
import com.example.algo.common.model.*;
import com.example.algo.common.model.enums.*;
import com.example.algo.execution.repository.OrderRepository;
import com.example.algo.execution.repository.UserScopedOrderRepository;
import com.example.algo.execution.config.OrderExecutionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {

  private final BrokerRouter brokerRouter;
  private final OrderRepository orderRepository;
  private final UserScopedOrderRepository userScopedOrderRepository;
  private final RiskManagementService riskManagementService;
  private final PositionSizingService positionSizingService;
  private final PortfolioService portfolioService;
  private final OrderExecutionConfig config;
  private final OrderTypeDecisionService orderTypeDecisionService;

  /** Process strategy signal and place order */
  @Transactional
  public Optional<Order> place(StrategySignalEvent signal) {
    try {
      log.info(
          "Processing strategy signal: {} {} @ ₹{} from {}",
          signal.getSide(),
          signal.getSymbol(),
          signal.getPrice(),
          signal.getStrategy());

      // Step 1: Create internal order from signal
      Order order = createOrderFromSignal(signal);

      // Step 2: Apply risk management
      RiskManagementService.RiskAssessment
              riskResult = riskManagementService.assessRisk(order, signal);
      if (!riskResult.isApproved()) {
        log.warn(
            "Order rejected by risk management: {} - {}", order.getId(), riskResult.getReason());
        Order rejectedOrder = order.toBuilder()
            .status(OrderStatus.REJECTED)
            .rejectionReason(riskResult.getReason())
            .build();
        return Optional.of(orderRepository.save(rejectedOrder));
      }

      // Step 3: Calculate position size
      PositionSizingService.PositionSizing
              sizing = positionSizingService.calculatePositionSize(signal, riskResult);
      order.setQuantity(sizing.getQuantity());
      order.setPositionSize(sizing.getPositionValue());
      order.setRiskAmount(sizing.getRiskAmount());

      // Step 4: Set risk management parameters
      order.setStopLoss(calculateStopLoss(signal.getPrice(), signal.getSide(), riskResult));
      order.setTakeProfit(calculateTakeProfit(signal.getPrice(), signal.getSide(), riskResult));

      // Step 5: Save order before broker placement
      order = order.toBuilder().status(OrderStatus.PENDING).build();
      order = userScopedOrderRepository.save(order);

      // Step 6: Place order with broker
      Optional<Order> placedOrder = placeOrderWithBroker(order);

      if (placedOrder.isPresent()) {
        // Step 7: Update order with broker response
        Order updatedOrder = placedOrder.get().toBuilder()
            .status(OrderStatus.PLACED)
            .placedAt(LocalDateTime.now())
            .build();
        updatedOrder = orderRepository.save(updatedOrder);

        // Step 8: Create stop-loss and take-profit orders if needed
        createChildOrders(updatedOrder);

        log.info(
            "✅ Order placed successfully: {} {} shares of {} @ ₹{}, Order ID: {}",
            updatedOrder.getSide(),
            updatedOrder.getQuantity(),
            updatedOrder.getSymbol(),
            updatedOrder.getPrice(),
            updatedOrder.getBrokerOrderId());

        return Optional.of(updatedOrder);
      } else {
        // Order placement failed
        Order failedOrder = order.toBuilder()
            .status(OrderStatus.FAILED)
            .rejectionReason("Failed to place order with broker")
            .build();
        order = orderRepository.save(failedOrder);

        log.error("Failed to place order with broker: {}", order.getId());
        return Optional.of(order);
      }

    } catch (Exception e) {
      log.error("Exception while processing order: {}", signal, e);
      return Optional.empty();
    }
  }

  /** Create Order entity from StrategySignalEvent */
  private Order createOrderFromSignal(StrategySignalEvent signal) {
    // Intelligently decide order type based on strategy and market conditions
    OrderTypeDecisionService.OrderTypeDecision decision =
        orderTypeDecisionService.decideEntryOrderType(signal);

    log.info(
        "Order type decision for {}: {} @ ₹{} - {}",
        signal.getSymbol(),
        decision.getOrderType(),
        decision.getPrice(),
        decision.getReasoning());

    return Order.builder()
        .id(UUID.randomUUID().toString())
        .signalId(generateSignalId(signal))
        .symbol(signal.getSymbol())
        .side(signal.getSide())
        .type(decision.getOrderType())
        .price(decision.getPrice())
        .limitPrice(decision.getLimitPrice())
        .stopPrice(decision.getStopPrice())
        .strategyName(signal.getStrategy())
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .status(OrderStatus.CREATED)
        .brokerName(determineBrokerForSymbol(signal.getSymbol()))
        .clientId(config.getDefaultClientId())
        .exchange(determineExchange(signal.getSymbol()))
        .product(config.getDefaultProduct()) // CNC/MIS/NRML
        .validity(config.getDefaultValidity()) // DAY/IOC
        .orderSource("ALGO")
        .retryCount(0)
        .tags("ENTRY_ORDER,CONFIDENCE_" + Math.round(decision.getConfidence() * 100))
        .build();
  }

  /** Place order with broker through broker-connector */
  private Optional<Order> placeOrderWithBroker(Order order) {
    try {
      String brokerName = order.getBrokerName();
      BrokerClient brokerClient = brokerRouter.byName(brokerName);

      if (brokerClient == null) {
        log.error("No broker client found for: {}", brokerName);
        return Optional.empty();
      }

      // Place order with broker
      Optional<Order> brokerResponse = brokerClient.placeOrder(order).blockOptional();

      if (brokerResponse.isPresent()) {
        Order placedOrder = brokerResponse.get();

        // Update with broker-specific details
        order.setBrokerOrderId(placedOrder.getBrokerOrderId());
        order.setCommission(placedOrder.getCommission());
        order.setTaxes(placedOrder.getTaxes());
        order.setStatusMessage(placedOrder.getStatusMessage());

        return Optional.of(order);
      }

      return Optional.empty();

    } catch (Exception e) {
      log.error("Error placing order with broker: {}", order.getId(), e);
      return Optional.empty();
    }
  }

  /** Create stop-loss and take-profit child orders */
  private void createChildOrders(Order parentOrder) {
    try {
      // Create Stop Loss order
      if (parentOrder.getStopLoss() > 0) {
        Order stopLossOrder = createStopLossOrder(parentOrder);
        orderRepository.save(stopLossOrder);
        log.info(
            "Created stop-loss order: {} @ ₹{}",
            stopLossOrder.getId(),
            stopLossOrder.getStopPrice());
      }

      // Create Take Profit order
      if (parentOrder.getTakeProfit() > 0) {
        Order takeProfitOrder = createTakeProfitOrder(parentOrder);
        orderRepository.save(takeProfitOrder);
        log.info(
            "Created take-profit order: {} @ ₹{}",
            takeProfitOrder.getId(),
            takeProfitOrder.getLimitPrice());
      }

    } catch (Exception e) {
      log.error("Error creating child orders for: {}", parentOrder.getId(), e);
    }
  }

  /** Create stop-loss order */
  private Order createStopLossOrder(Order parentOrder) {
    OrderSide slSide = parentOrder.getSide() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;

    // Intelligently decide stop-loss order type
    OrderTypeDecisionService.OrderTypeDecision decision =
        orderTypeDecisionService.decideStopLossOrderType(
            parentOrder.getAveragePrice(),
            parentOrder.getStopLoss(),
            parentOrder.getSide(),
            parentOrder.getSymbol());

    log.info(
        "Stop-loss order type decision for {}: {} - {}",
        parentOrder.getSymbol(),
        decision.getOrderType(),
        decision.getReasoning());

    return Order.builder()
        .id(UUID.randomUUID().toString())
        .parentOrderId(parentOrder.getId())
        .symbol(parentOrder.getSymbol())
        .side(slSide)
        .quantity(parentOrder.getQuantity())
        .type(decision.getOrderType())
        .stopPrice(decision.getStopPrice())
        .price(decision.getPrice())
        .limitPrice(decision.getLimitPrice())
        .strategyName(parentOrder.getStrategyName())
        .brokerName(parentOrder.getBrokerName())
        .clientId(parentOrder.getClientId())
        .exchange(parentOrder.getExchange())
        .product(parentOrder.getProduct())
        .validity("DAY")
        .status(OrderStatus.CREATED)
        .createdAt(LocalDateTime.now())
        .orderSource("ALGO_SL")
        .tags("EXIT_ORDER,STOP_LOSS")
        .build();
  }

  /** Create take-profit order */
  private Order createTakeProfitOrder(Order parentOrder) {
    OrderSide tpSide = parentOrder.getSide() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;

    // Intelligently decide take-profit order type
    OrderTypeDecisionService.OrderTypeDecision decision =
        orderTypeDecisionService.decideTakeProfitOrderType(
            parentOrder.getAveragePrice(),
            parentOrder.getTakeProfit(),
            parentOrder.getSide(),
            parentOrder.getSymbol());

    log.info(
        "Take-profit order type decision for {}: {} - {}",
        parentOrder.getSymbol(),
        decision.getOrderType(),
        decision.getReasoning());

    return Order.builder()
        .id(UUID.randomUUID().toString())
        .parentOrderId(parentOrder.getId())
        .symbol(parentOrder.getSymbol())
        .side(tpSide)
        .quantity(parentOrder.getQuantity())
        .type(decision.getOrderType())
        .stopPrice(decision.getStopPrice())
        .limitPrice(decision.getLimitPrice())
        .price(decision.getPrice())
        .strategyName(parentOrder.getStrategyName())
        .brokerName(parentOrder.getBrokerName())
        .clientId(parentOrder.getClientId())
        .exchange(parentOrder.getExchange())
        .product(parentOrder.getProduct())
        .validity("DAY")
        .status(OrderStatus.CREATED)
        .createdAt(LocalDateTime.now())
        .orderSource("ALGO_TP")
        .tags("EXIT_ORDER,TAKE_PROFIT")
        .build();
  }

  /** Calculate stop-loss price */
  private double calculateStopLoss(double entryPrice, OrderSide side, RiskManagementService.RiskAssessment risk) {
    double stopLossPercentage = risk.getStopLossPercentage();

    if (side == OrderSide.BUY) {
      return entryPrice * (1 - stopLossPercentage / 100.0);
    } else {
      return entryPrice * (1 + stopLossPercentage / 100.0);
    }
  }

  /** Calculate take-profit price */
  private double calculateTakeProfit(double entryPrice, OrderSide side, RiskManagementService.RiskAssessment risk) {
    double takeProfitPercentage = risk.getTakeProfitPercentage();

    if (side == OrderSide.BUY) {
      return entryPrice * (1 + takeProfitPercentage / 100.0);
    } else {
      return entryPrice * (1 - takeProfitPercentage / 100.0);
    }
  }

  /** Determine broker for symbol */
  private String determineBrokerForSymbol(String symbol) {
    // Logic to route symbols to appropriate brokers
    // For now, default to upstox
    return config.getDefaultBroker();
  }

  /** Determine exchange for symbol */
  private String determineExchange(String symbol) {
    // NSE for most stocks, BSE for some
    if (symbol.contains("NSE") || symbol.startsWith("NIFTY")) {
      return "NSE";
    }
    return "NSE"; // Default to NSE
  }

  /** Generate unique signal ID */
  private String generateSignalId(StrategySignalEvent signal) {
    return String.format("%s_%s_%d", signal.getStrategy(), signal.getSymbol(), signal.getTs());
  }

  /** Cancel order */
  @Transactional
  public boolean cancelOrder(String orderId) {
    try {
      Optional<Order> orderOpt = orderRepository.findById(orderId);
      if (orderOpt.isEmpty()) {
        log.warn("Order not found for cancellation: {}", orderId);
        return false;
      }

      Order order = orderOpt.get();
      if (!order.canBeCancelled()) {
        log.warn("Order cannot be cancelled: {} - Status: {}", orderId, order.getStatus());
        return false;
      }

      // Cancel with broker
      BrokerClient brokerClient = brokerRouter.byName(order.getBrokerName());
      Order cancelledBrokerOrder = brokerClient.cancelOrder(order.getBrokerOrderId()).block();

      if (cancelledBrokerOrder != null) {
        Order cancelledOrder = order.toBuilder()
            .status(OrderStatus.CANCELLED)
            .cancelledAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        orderRepository.save(cancelledOrder);

        log.info("✅ Order cancelled successfully: {}", orderId);
        return true;
      }

      return false;

    } catch (Exception e) {
      log.error("Error cancelling order: {}", orderId, e);
      return false;
    }
  }

  /** Get order by ID - user-scoped */
  public Optional<Order> getOrder(String orderId) {
    return userScopedOrderRepository.findById(orderId);
  }

  /** Get orders by symbol - user-scoped */
  public List<Order> getOrdersBySymbol(String symbol) {
    return userScopedOrderRepository.findBySymbolOrderByCreatedAtDesc(symbol);
  }

  /** Get active orders - user-scoped */
  public List<Order> getActiveOrders() {
    return userScopedOrderRepository.findActiveOrders();
  }

  /** Get orders by strategy - user-scoped */
  public List<Order> getOrdersByStrategy(String strategyName) {
    return userScopedOrderRepository.findByStrategyNameOrderByCreatedAtDesc(strategyName);
  }
}
