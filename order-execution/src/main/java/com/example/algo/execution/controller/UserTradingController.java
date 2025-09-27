package com.example.algo.execution;

import com.example.algo.common.security.UserContext;
import com.example.algo.execution.repository.UserPositionRepository;
import com.example.algo.execution.repository.UserOrderAuditRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Slf4j
@SecurityRequirement(name = "Bearer Authentication")
public class UserTradingController {

    private final UserPositionRepository positionRepository;
    private final UserOrderAuditRepository auditRepository;

    @GetMapping("/positions")
    @Operation(summary = "Get current user's positions")
    public ResponseEntity<List<UserPosition>> getUserPositions() {
        UserContext user = UserContext.getCurrentUser();
        log.info("Fetching positions for user: {}", user.getUserId());

        List<UserPosition> positions = positionRepository.findAllForCurrentUser();
        return ResponseEntity.ok(positions);
    }

    @GetMapping("/positions/{symbol}")
    @Operation(summary = "Get position for specific symbol")
    public ResponseEntity<UserPosition> getPositionBySymbol(@PathVariable String symbol) {
        UserContext user = UserContext.getCurrentUser();
        log.info("Fetching position for user {} symbol: {}", user.getUserId(), symbol);

        return positionRepository.findBySymbolForCurrentUser(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/orders")
    @Operation(summary = "Get user's order history")
    public ResponseEntity<Page<UserOrderAudit>> getOrderHistory(Pageable pageable) {
        UserContext user = UserContext.getCurrentUser();
        log.info("Fetching order history for user: {}", user.getUserId());

        Page<UserOrderAudit> orders = auditRepository.findAllForCurrentUser(pageable);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/orders/symbol/{symbol}")
    @Operation(summary = "Get orders for specific symbol")
    public ResponseEntity<List<UserOrderAudit>> getOrdersBySymbol(@PathVariable String symbol) {
        UserContext user = UserContext.getCurrentUser();
        log.info("Fetching orders for user {} symbol: {}", user.getUserId(), symbol);

        List<UserOrderAudit> orders = auditRepository.findBySymbolForCurrentUser(symbol);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/summary")
    @Operation(summary = "Get user's trading summary")
    public ResponseEntity<Map<String, Object>> getTradingSummary() {
        UserContext user = UserContext.getCurrentUser();
        log.info("Fetching trading summary for user: {}", user.getUserId());

        Long successfulOrders = auditRepository.countSuccessfulOrdersForCurrentUser();
        Long failedOrders = auditRepository.countFailedOrdersForCurrentUser();
        Double totalPnL = positionRepository.getTotalPnLForCurrentUser();
        List<UserPosition> activePositions = positionRepository.findActivePositionsForCurrentUser();

        Map<String, Object> summary = Map.of(
                "userId", user.getUserId(),
                "successfulOrders", successfulOrders != null ? successfulOrders : 0L,
                "failedOrders", failedOrders != null ? failedOrders : 0L,
                "totalPnL", totalPnL != null ? totalPnL : 0.0,
                "activePositions", activePositions.size(),
                "positionValue", calculateTotalPositionValue(activePositions)
        );

        return ResponseEntity.ok(summary);
    }

    // Admin-only endpoint
    @GetMapping("/admin/user/{userId}/positions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: Get positions for specific user")
    public ResponseEntity<List<UserPosition>> getUserPositionsAdmin(@PathVariable String userId) {
        UserContext currentUser = UserContext.getCurrentUser();
        log.info("Admin {} accessing positions for user: {}", currentUser.getUserId(), userId);

        List<UserPosition> positions = positionRepository.findByUserIdForAdmin(userId);
        return ResponseEntity.ok(positions);
    }

    private double calculateTotalPositionValue(List<UserPosition> positions) {
        return positions.stream()
                .mapToDouble(p -> p.getQuantity() * p.getAveragePrice().doubleValue())
                .sum();
    }
}
