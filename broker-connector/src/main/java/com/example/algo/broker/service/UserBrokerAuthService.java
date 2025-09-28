package com.example.algo.broker.service;

import com.example.algo.broker.auth.SecureUserBrokerTokenRepo;
import com.example.algo.broker.auth.UserBrokerToken;
import com.example.algo.broker.core.BrokerClient;
import com.example.algo.common.model.Order;
import com.example.algo.common.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserBrokerAuthService {

    private final SecureUserBrokerTokenRepo tokenRepo;

    public Mono<Order> executeWithUserCredentials(BrokerClient client, Order order, String userId) {
        return Mono.fromCallable(() -> {
                    // Validate current user can access this user's broker tokens
                    UserContext currentUser = UserContext.getCurrentUser();
                    if (!currentUser.getUserId().equals(userId) && !currentUser.hasPermission("ACCESS_USER_BROKER_TOKENS")) {
                        throw new SecurityException("Cannot access broker credentials for user: " + userId);
                    }

                    // Get user's broker token (automatically filtered by user context in repository)
                    UserBrokerToken token = tokenRepo.findTopByUserIdAndBrokerOrderByUpdatedAtDesc(userId, client.name())
                            .orElseThrow(() -> new SecurityException("No valid broker token found for " + client.name()));

                    // Validate token belongs to the requested user
                    if (!token.getUserId().equals(userId)) {
                        throw new SecurityException("Token user mismatch");
                    }

                    return token;
                })
                .flatMap(token -> {
                    // Execute order with user's specific credentials
                    log.info("Executing order {} for user {} using broker {}",
                            order.getId(), userId, client.name());
                    return client.placeOrder(order);
                })
                .doOnSuccess(result ->
                        log.info("Order executed successfully for user {}: {}", userId, result.getId()))
                .doOnError(error ->
                        log.error("Order execution failed for user {}: {}", userId, error.getMessage()));
    }
}
