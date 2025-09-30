package com.example.algo.broker.impl;

import com.example.algo.broker.core.BrokerClient;
import com.example.algo.broker.upstox.UpstoxModels;
import com.example.algo.common.model.*;
import com.example.algo.common.model.enums.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.algo.common.model.enums.OrderStatus.CANCELLED;
import static com.example.algo.common.model.enums.OrderStatus.PENDING;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpstoxConnector implements BrokerClient {
  private final WebClient.Builder builder;
  private final ObjectMapper objectMapper;

  @Value("${brokers.registry.upstox.baseUrl:https://api.upstox.com}")
  String base;

  @Value("${UPSTOX_ACCESS_TOKEN:}")
  String accessToken;

  @Value("${brokers.registry.upstox.websocket:wss://ws.upstox.com/v2/feed}")
  String websocketUrl;

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
        .map(s -> o.builder().id(UUID.randomUUID().toString()).status(PENDING).build());
  }

  @Override
  public Mono<Order> cancelOrder(String id) {
    return client()
        .delete()
        .uri("/v2/order/cancel?order_id={id}", id)
        .retrieve()
        .bodyToMono(String.class)
        .map(s -> Order.builder().id(id).status(CANCELLED).build());
  }

  @Override
  public Mono<Order> getOrderStatus(String orderId) {
    return client()
        .get()
        .uri("/v2/order/details?order_id={orderId}", orderId)
        .retrieve()
        .bodyToMono(String.class)
        .map(response -> {
          // Parse Upstox order response and convert to our Order model
          // For now, return a basic order with mock status
          return Order.builder()
              .id(orderId)
              .brokerOrderId(orderId)
              .status(OrderStatus.PLACED) // This should be parsed from actual response
              .statusMessage("Order status retrieved from Upstox")
              .filledQuantity(0)
              .remainingQuantity(100)
              .averagePrice(0.0)
              .commission(0.0)
              .taxes(0.0)
              .totalCost(0.0)
              .updatedAt(LocalDateTime.now())
              .build();
        })
        .doOnError(error -> log.error("Failed to get order status for {}", orderId, error))
        .onErrorReturn(Order.builder()
            .id(orderId)
            .brokerOrderId(orderId)
            .status(OrderStatus.FAILED)
            .statusMessage("Failed to retrieve order status")
            .build());
  }

  @Override
  public Flux<Instrument> fetchInstruments() {
    return fetchInstrumentsOnce().flatMapMany(Flux::fromIterable);
  }

  @Override
  public Mono<List<Instrument>> fetchInstrumentsOnce() {
    return client()
        .get()
        .uri("/v2/market/instruments/NSE_EQ")
        .retrieve()
        .bodyToMono(String.class)
        .map(this::parseInstruments)
        .doOnError(error -> log.error("Failed to fetch instruments", error))
        .onErrorReturn(List.of());
  }

  private List<Instrument> parseInstruments(String csvData) {
    try {
      String[] lines = csvData.split("\n");
      return Arrays.stream(lines)
          .skip(1) // Skip header
          .limit(100) // Limit for demo
          .map(this::parseInstrumentLine)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.error("Error parsing instruments", e);
      return List.of();
    }
  }

  private Instrument parseInstrumentLine(String line) {
    try {
      String[] parts = line.split(",");
      if (parts.length >= 4) {
        return Instrument.builder()
            .symbol(parts[1])
            .name(parts[2])
            .exchange("NSE")
            .instrumentType("EQ")
            .build();
      }
    } catch (Exception e) {
      log.debug("Error parsing instrument line: {}", line);
    }
    return null;
  }

  @Override
  public Mono<List<Order>> fetchPositions() {
    return Mono.just(List.of());
  }

  @Override
  public Flux<Quote> getMarketData(Collection<String> symbols) {
    return Flux.fromIterable(symbols)
        .flatMap(this::getQuoteForSymbol)
        .delayElements(java.time.Duration.ofSeconds(1));
  }

  private Mono<Quote> getQuoteForSymbol(String symbol) {
    String instrumentKey = "NSE_EQ|" + symbol;
    return client()
        .get()
        .uri("/v2/market/quote/ltp?instrument_key={key}", instrumentKey)
        .retrieve()
        .bodyToMono(String.class)
        .map(response -> parseQuoteResponse(symbol, response))
        .doOnError(error -> log.error("Failed to fetch quote for {}", symbol, error))
        .onErrorReturn(Quote.builder().symbol(symbol).ltp(0.0).ts(System.currentTimeMillis()).build());
  }

  private Quote parseQuoteResponse(String symbol, String response) {
    try {
      UpstoxModels.UpstoxResponse<Map<String, UpstoxModels.MarketQuote>> upstoxResponse =
          objectMapper.readValue(response, new TypeReference<UpstoxModels.UpstoxResponse<Map<String, UpstoxModels.MarketQuote>>>() {});

      if (upstoxResponse.getData() != null && !upstoxResponse.getData().isEmpty()) {
        UpstoxModels.MarketQuote quote = upstoxResponse.getData().values().iterator().next();
        return Quote.builder()
            .symbol(symbol)
            .ltp(quote.getLastTradedPrice())
            .ts(System.currentTimeMillis())
            .build();
      }
    } catch (Exception e) {
      log.error("Error parsing quote response for {}", symbol, e);
    }
    return Quote.builder().symbol(symbol).ltp(0.0).ts(System.currentTimeMillis()).build();
  }

  // Historical Data API
  public Mono<List<Candle>> getHistoricalData(String symbol, TimeInterval interval, LocalDate fromDate, LocalDate toDate) {
    String instrumentKey = "NSE_EQ|" + symbol;
    String from = fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
    String to = toDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

    return client()
        .get()
        .uri("/v2/historical-candle/{instrumentKey}/{interval}/{to}/{from}",
             instrumentKey, interval.getUpstoxValue(), to, from)
        .retrieve()
        .bodyToMono(String.class)
        .map(response -> parseHistoricalData(symbol, response))
        .doOnError(error -> log.error("Failed to fetch historical data for {}", symbol, error))
        .onErrorReturn(List.of());
  }

  private List<Candle> parseHistoricalData(String symbol, String response) {
    try {
      UpstoxModels.UpstoxResponse<UpstoxModels.HistoricalData> upstoxResponse =
          objectMapper.readValue(response, new TypeReference<UpstoxModels.UpstoxResponse<UpstoxModels.HistoricalData>>() {});

      if (upstoxResponse.getData() != null && upstoxResponse.getData().getCandles() != null) {
        return upstoxResponse.getData().getCandles().stream()
            .map(candleData -> convertToCandle(symbol, candleData))
            .collect(Collectors.toList());
      }
    } catch (Exception e) {
      log.error("Error parsing historical data for {}", symbol, e);
    }
    return List.of();
  }

  private Candle convertToCandle(String symbol, UpstoxModels.CandleData candleData) {
    return Candle.builder()
        .symbol(symbol)
        .timestamp(LocalDateTime.parse(candleData.getTimestamp().replace("Z", ""),
                   DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        .open(candleData.getOpen())
        .high(candleData.getHigh())
        .low(candleData.getLow())
        .close(candleData.getClose())
        .volume(candleData.getVolume())
        .build();
  }
}
