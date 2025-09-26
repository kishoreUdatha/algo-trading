package com.example.algo.broker.core;

import com.example.algo.common.model.*;
import reactor.core.publisher.*;
import java.util.*;

public interface BrokerClient {
  Mono<Order> placeOrder(Order order);

  Mono<Order> cancelOrder(String orderId);

  Flux<Instrument> fetchInstruments();

  Mono<List<Instrument>> fetchInstrumentsOnce();

  Mono<List<Order>> fetchPositions();

  Flux<Quote> getMarketData(Collection<String> symbols);

  String name();
}
