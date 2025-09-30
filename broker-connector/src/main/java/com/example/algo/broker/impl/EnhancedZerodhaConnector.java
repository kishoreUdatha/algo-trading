package com.example.algo.broker.impl;

import com.example.algo.broker.core.BrokerClient;
import com.example.algo.common.exception.BrokerException;
import com.example.algo.common.model.*;
import com.example.algo.common.model.enums.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;

import static com.example.algo.common.model.enums.OrderStatus.CANCELLED;
import static com.example.algo.common.model.enums.OrderStatus.CANCEL_FAILED;
import static com.example.algo.common.model.enums.OrderStatus.FAILED;
import static com.example.algo.common.model.enums.OrderStatus.PENDING;

@Component
@RequiredArgsConstructor
@Slf4j
public class EnhancedZerodhaConnector implements BrokerClient {

    private final WebClient.Builder builder;

    @Value("${brokers.registry.zerodha.baseUrl:https://api.kite.trade}")
    private String baseUrl;

    @Value("${brokers.registry.zerodha.apiKey}")
    private String apiKey;

    @Value("${KITE_ACCESS_TOKEN}")
    private String accessToken;

    private WebClient client() {
        return builder
                .baseUrl(baseUrl)
                .defaultHeaders(h -> {
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
    @CircuitBreaker(name = "zerodha", fallbackMethod = "placeOrderFallback")
    @TimeLimiter(name = "zerodha")
    public Mono<Order> placeOrder(Order order) {
        log.info("Placing order: {} {} {} @ {}",
                order.getSymbol(), order.getSide(), order.getQty(), order.getPrice());

        return client()
                .post()
                .uri("/orders/regular")
                .bodyValue(buildOrderRequest(order))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .map(body -> new BrokerException("zerodha", "CLIENT_ERROR",
                                        "Order placement failed: " + body)))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .map(body -> new BrokerException("zerodha", "SERVER_ERROR",
                                        "Broker server error: " + body)))
                .bodyToMono(Map.class)
                .map(response -> mapToOrder(order, response))
                .doOnSuccess(o -> log.info("Order placed successfully: {}", o.getId()))
                .doOnError(error -> log.error("Order placement failed for {}: {}",
                        order.getSymbol(), error.getMessage()))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                        .filter(throwable -> !(throwable instanceof BrokerException &&
                                "CLIENT_ERROR".equals(((BrokerException) throwable).getErrorCode()))));
    }

    public Mono<Order> placeOrderFallback(Order order, Exception ex) {
        log.error("Circuit breaker activated for order placement: {}", ex.getMessage());
        return Mono.just(order.toBuilder()
                .status(FAILED)
                .build());
    }

    @Override
    @CircuitBreaker(name = "zerodha", fallbackMethod = "cancelOrderFallback")
    public Mono<Order> cancelOrder(String orderId) {
        log.info("Cancelling order: {}", orderId);

        return client()
                .delete()
                .uri("/orders/regular/{orderId}", orderId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .map(body -> new BrokerException("zerodha", "CANCEL_FAILED",
                                        "Order cancellation failed: " + body)))
                .bodyToMono(Map.class)
                .map(response -> Order.builder()
                        .id(orderId)
                        .status(CANCELLED)
                        .build())
                .doOnSuccess(o -> log.info("Order cancelled: {}", orderId))
                .doOnError(error -> log.error("Order cancellation failed for {}: {}",
                        orderId, error.getMessage()));
    }

    @Override
    @CircuitBreaker(name = "zerodha", fallbackMethod = "getOrderStatusFallback")
    public Mono<Order> getOrderStatus(String orderId) {
        log.info("Getting order status: {}", orderId);

        return client()
                .get()
                .uri("/orders/{orderId}", orderId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .map(body -> new BrokerException("zerodha", "STATUS_FAILED",
                                        "Order status retrieval failed: " + body)))
                .bodyToMono(Map.class)
                .map(response -> mapOrderStatusResponse(orderId, response))
                .doOnSuccess(o -> log.info("Order status retrieved: {} - {}", orderId, o.getStatus()))
                .doOnError(error -> log.error("Order status retrieval failed for {}: {}",
                        orderId, error.getMessage()))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(300)));
    }

    public Mono<Order> getOrderStatusFallback(String orderId, Exception ex) {
        log.error("Circuit breaker activated for order status retrieval: {}", ex.getMessage());
        return Mono.just(Order.builder()
                .id(orderId)
                .brokerOrderId(orderId)
                .status(FAILED)
                .statusMessage("Failed to retrieve order status: " + ex.getMessage())
                .build());
    }

    private Order mapOrderStatusResponse(String orderId, Map<String, Object> response) {
        // Parse Zerodha order status response
        // This is a simplified implementation - real implementation would parse actual Zerodha response
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data != null && !data.isEmpty()) {
            Map<String, Object> orderData = (Map<String, Object>) ((List<?>) data).get(0);

            String status = orderData.get("status").toString();
            OrderStatus orderStatus = mapZerodhaStatus(status);

            return Order.builder()
                    .id(orderId)
                    .brokerOrderId(orderId)
                    .status(orderStatus)
                    .statusMessage("Status from Zerodha: " + status)
                    .filledQuantity(Integer.parseInt(orderData.getOrDefault("filled_quantity", "0").toString()))
                    .remainingQuantity(Integer.parseInt(orderData.getOrDefault("pending_quantity", "0").toString()))
                    .averagePrice(Double.parseDouble(orderData.getOrDefault("average_price", "0").toString()))
                    .commission(Double.parseDouble(orderData.getOrDefault("commission", "0").toString()))
                    .taxes(Double.parseDouble(orderData.getOrDefault("taxes", "0").toString()))
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();
        }

        // Fallback if no data
        return Order.builder()
                .id(orderId)
                .brokerOrderId(orderId)
                .status(OrderStatus.PENDING)
                .statusMessage("Order status pending")
                .build();
    }

    private OrderStatus mapZerodhaStatus(String zerodhaStatus) {
        return switch (zerodhaStatus.toUpperCase()) {
            case "COMPLETE" -> OrderStatus.FILLED;
            case "CANCELLED" -> OrderStatus.CANCELLED;
            case "REJECTED" -> OrderStatus.REJECTED;
            case "OPEN" -> OrderStatus.PLACED;
            case "TRIGGER PENDING" -> OrderStatus.PENDING;
            default -> OrderStatus.PENDING;
        };
    }

    public Mono<Order> cancelOrderFallback(String orderId, Exception ex) {
        log.error("Circuit breaker activated for order cancellation: {}", ex.getMessage());
        return Mono.just(Order.builder()
                .id(orderId)
                .status(CANCEL_FAILED)
                .build());
    }

    @Override
    @CircuitBreaker(name = "zerodha", fallbackMethod = "getMarketDataFallback")
    public Flux<Quote> getMarketData(Collection<String> symbols) {
        return Flux.fromIterable(symbols)
                .flatMap(this::getQuoteForSymbol)
                .onErrorContinue((error, obj) ->
                        log.error("Failed to get quote for {}: {}", obj, error.getMessage()));
    }

    public Flux<Quote> getMarketDataFallback(Collection<String> symbols, Exception ex) {
        log.error("Circuit breaker activated for market data: {}", ex.getMessage());
        return Flux.empty();
    }

    private Mono<Quote> getQuoteForSymbol(String symbol) {
        return client()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/quote")
                        .queryParam("i", "NSE:" + symbol)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> mapToQuote(symbol, response))
                .timeout(Duration.ofSeconds(5));
    }

    private Map<String, Object> buildOrderRequest(Order order) {
        Map<String, Object> request = new HashMap<>();
        request.put("exchange", "NSE");
        request.put("tradingsymbol", order.getSymbol());
        request.put("transaction_type", order.getSide() == OrderSide.BUY ? "BUY" : "SELL");
        request.put("quantity", order.getQty());
        request.put("product", "MIS");
        request.put("order_type", order.getType() == OrderType.MARKET ? "MARKET" : "LIMIT");
        if (order.getType() == OrderType.LIMIT) {
            request.put("price", order.getPrice());
        }
        return request;
    }

    private Order mapToOrder(Order original, Map<String, Object> response) {
        String orderId = response.get("order_id") != null ?
                response.get("order_id").toString() : UUID.randomUUID().toString();

        return original.toBuilder()
                .id(orderId)
                .status(PENDING)
                .build();
    }

    private Quote mapToQuote(String symbol, Map<String, Object> response) {
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data != null && data.containsKey(symbol)) {
            Map<String, Object> quote = (Map<String, Object>) data.get(symbol);
            double ltp = Double.parseDouble(quote.get("last_price").toString());
            return Quote.builder()
                    .symbol(symbol)
                    .ltp(ltp)
                    .ts(System.currentTimeMillis())
                    .build();
        }
        throw new BrokerException("zerodha", "INVALID_RESPONSE", "Invalid quote response");
    }

    @Override
    public Flux<Instrument> fetchInstruments() {
        return Flux.empty(); // TODO: implement
    }

    @Override
    public Mono<List<Instrument>> fetchInstrumentsOnce() {
        return Mono.just(List.of()); // TODO: implement
    }

    @Override
    public Mono<List<Order>> fetchPositions() {
        return Mono.just(List.of()); // TODO: implement
    }
}
