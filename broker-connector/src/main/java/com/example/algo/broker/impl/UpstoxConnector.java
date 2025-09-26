package com.example.algo.broker.impl;

import com.example.algo.broker.core.BrokerClient;
import com.example.algo.common.model.*;
import com.example.algo.common.model.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.*;
import java.util.*;

@Component
@RequiredArgsConstructor
public class UpstoxConnector implements BrokerClient {
  private final WebClient.Builder builder;

  @Value("${brokers.registry.upstox.baseUrl:https://api.upstox.com}")
  String base;

  @Value("${UPSTOX_ACCESS_TOKEN:}")
  String accessToken;

  private WebClient client() {
    return builder
        .baseUrl(base)
        .defaultHeaders(h -> h.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .build();
  }

  @Override
  public String name() {
    return "upstox";
  }

  @Override
  public Mono<Order> placeOrder(Order o) {
    return client()
        .post()
        .uri("/v2/order/place")
        .bodyValue(
            Map.of(
                "instrument_token",
                o.getSymbol(),
                "transaction_type",
                o.getSide() == OrderSide.BUY ? "BUY" : "SELL",
                "quantity",
                o.getQty(),
                "order_type",
                o.getType() == OrderType.MARKET ? "MARKET" : "LIMIT",
                "price",
                o.getPrice()))
        .retrieve()
        .bodyToMono(String.class)
        .map(s -> o.builder().id(UUID.randomUUID().toString()).status("PENDING").build());
  }

  @Override
  public Mono<Order> cancelOrder(String id) {
    return client()
        .delete()
        .uri("/v2/order/cancel?order_id={id}", id)
        .retrieve()
        .bodyToMono(String.class)
        .map(s -> Order.builder().id(id).status("CANCELLED").build());
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
