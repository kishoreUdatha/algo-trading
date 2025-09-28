package com.example.algo.execution.repository;

import com.example.algo.execution.mdel.UserOrderAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface UserOrderAuditRepository extends JpaRepository<UserOrderAudit, Long> {

    // User-specific queries (automatically filtered)
    @Query("SELECT a FROM UserOrderAudit a WHERE a.userId = :#{T(com.example.algo.common.security.UserContext).getCurrentUser().userId} ORDER BY a.createdAt DESC")
    Page<UserOrderAudit> findAllForCurrentUser(Pageable pageable);

    @Query("SELECT a FROM UserOrderAudit a WHERE a.userId = :#{T(com.example.algo.common.security.UserContext).getCurrentUser().userId} AND a.symbol = :symbol ORDER BY a.createdAt DESC")
    List<UserOrderAudit> findBySymbolForCurrentUser(@Param("symbol") String symbol);

    @Query("SELECT a FROM UserOrderAudit a WHERE a.userId = :#{T(com.example.algo.common.security.UserContext).getCurrentUser().userId} AND a.createdAt BETWEEN :startTime AND :endTime")
    List<UserOrderAudit> findByTimeRangeForCurrentUser(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    @Query("SELECT COUNT(a) FROM UserOrderAudit a WHERE a.userId = :#{T(com.example.algo.common.security.UserContext).getCurrentUser().userId} AND a.eventType = 'ORDER_SUCCESS'")
    Long countSuccessfulOrdersForCurrentUser();

    @Query("SELECT COUNT(a) FROM UserOrderAudit a WHERE a.userId = :#{T(com.example.algo.common.security.UserContext).getCurrentUser().userId} AND a.eventType = 'ORDER_FAILURE'")
    Long countFailedOrdersForCurrentUser();
}
