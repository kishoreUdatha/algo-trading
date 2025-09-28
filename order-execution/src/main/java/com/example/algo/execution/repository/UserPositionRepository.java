package com.example.algo.execution.repository;


import com.example.algo.execution.mdel.UserPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPositionRepository extends JpaRepository<UserPosition, Long> {

    // Automatically filtered by current user context
    @Query("SELECT p FROM UserPosition p WHERE p.userId = :#{T(com.example.algo.common.security.UserContext).getCurrentUser().userId} AND p.symbol = :symbol")
    Optional<UserPosition> findBySymbolForCurrentUser(@Param("symbol") String symbol);

    @Query("SELECT p FROM UserPosition p WHERE p.userId = :#{T(com.example.algo.common.security.UserContext).getCurrentUser().userId}")
    List<UserPosition> findAllForCurrentUser();

    @Query("SELECT p FROM UserPosition p WHERE p.userId = :#{T(com.example.algo.common.security.UserContext).getCurrentUser().userId} AND p.quantity != 0")
    List<UserPosition> findActivePositionsForCurrentUser();

    @Query("SELECT SUM(p.realizedPnL + p.unrealizedPnL) FROM UserPosition p WHERE p.userId = :#{T(com.example.algo.common.security.UserContext).getCurrentUser().userId}")
    Double getTotalPnLForCurrentUser();

    // Admin queries (require special permissions)
    @Query("SELECT p FROM UserPosition p WHERE p.userId = :userId")
    List<UserPosition> findByUserIdForAdmin(@Param("userId") String userId);
}
