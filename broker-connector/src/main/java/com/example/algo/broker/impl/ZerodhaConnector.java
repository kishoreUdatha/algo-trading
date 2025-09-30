package com.example.algo.broker.impl;

import com.example.algo.broker.core.BrokerClient;
import com.example.algo.common.model.*;
import com.example.algo.common.model.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.*;
import java.util.*;

import static com.example.algo.common.model.enums.OrderStatus.CANCELLED;
import static com.example.algo.common.model.enums.OrderStatus.PENDING;

@Component
@RequiredArgsConstructor
public class ZerodhaConnector implements BrokerClient {
  private final WebClient.Builder builder;

  @Value("${brokers.registry.zerodha.baseUrl:https://api.kite.trade}")
  String base;

  @Value("${brokers.registry.zerodha.apiKey:demo}")
  String apiKey;

  @Value("${KITE_ACCESS_TOKEN:}")
  String accessToken;

  private WebClient client() {
    return builder
        .baseUrl(base)
        .defaultHeaders(
            h -> {
              h.add("X-Kite-Version", "3");
              h.add(HttpHeaders.AUTHORIZATION, "token " + apiKey + ":" + accessToken);
            })
        .build();
  }

  @Override
  public String name() {
    return "zerodha";
  }

  @Override
  public Mono<Order> placeOrder(Order o) {
    return client()
        .post()
        .uri("/orders/regular")
        .bodyValue(
            Map.of(
                "exchange",
                "NSE",
                "tradingsymbol",
                o.getSymbol(),
                "transaction_type",
                o.getSide() == OrderSide.BUY ? "BUY" : "SELL",
                "quantity",
                o.getQty(),
                "product",
                "MIS",
                "order_type",
                o.getType() == OrderType.MARKET ? "MARKET" : "LIMIT",
                "price",
                o.getPrice()))
        .retrieve()
        .bodyToMono(String.class)
        .map(s -> o.builder().id(UUID.randomUUID().toString()).status(PENDING).build());
  }

  @Override
  public Mono<Order> cancelOrder(String id) {
    return client()
        .delete()
        .uri("/orders/regular/{id}", id)
        .retrieve()
        .bodyToMono(String.class)
        .map(s -> Order.builder().id(id).status(CANCELLED).build());
  }

  @Override
  public Mono<Order> getOrderStatus(String orderId) {
    return client()
        .get()
        .uri("/orders/{orderId}", orderId)
        .retrieve()
        .bodyToMono(String.class)
        .map(response -> {
          // Parse Zerodha order response and convert to our Order model
          // For now, return a basic order with mock status
          return Order.builder()
              .id(orderId)
              .brokerOrderId(orderId)
              .status(OrderStatus.PLACED) // This should be parsed from actual response
              .statusMessage("Order status retrieved from Zerodha")
              .filledQuantity(0)
              .remainingQuantity(100)
              .averagePrice(0.0)
              .commission(0.0)
              .taxes(0.0)
              .totalCost(0.0)
              .updatedAt(java.time.LocalDateTime.now())
              .build();
        })
        .onErrorReturn(Order.builder()
            .id(orderId)
            .brokerOrderId(orderId)
            .status(OrderStatus.FAILED)
            .statusMessage("Failed to retrieve order status")
            .build());
  }

  @Override
  public Flux<Instrument> fetchInstruments() {
    return Flux.empty();
  }

  @Override
  public Mono<List<Instrument>> fetchInstrumentsOnce() {
    return Mono.just(List.of());
  }

  @Override
  public Mono<List<Order>> fetchPositions() {
    return Mono.just(List.of());
  }

  @Override
  public Flux<Quote> getMarketData(Collection<String> symbols) {
    return Flux.empty();
  }
}
