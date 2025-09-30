/*
package com.example.algo.broker;

import com.example.algo.broker.auth.UserBrokerToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface SecureUserBrokerTokenRepo extends JpaRepository<UserBrokerToken, Long> {

    // All queries
    automatically filtered by user context
    @Query("SELECT t FROM UserBrokerToken t WHERE t.userId = :#{T(com.example.algo.common.security.UserContext).getCurrentUser().userId} AND t.broker = :broker ORDER BY t.updatedAt DESC")
    Optional<UserBrokerToken> findCurrentTokenForBroker(@Param("broker") String broker);

    @Query("SELECT t FROM UserBrokerToken t WHERE t.userId = :#{T(com.example.algo.common.security.UserContext).getCurrentUser().userId}")
    List<UserBrokerToken> findAllUserTokens();

    @Query("SELECT t FROM UserBrokerToken t WHERE t.userId = :#{T(com.example.algo.common.security.UserContext).getCurrentUser().userId} AND t.broker = :broker")
    List<UserBrokerToken> findByBroker(@Param("broker") String broker);

    // Admin queries (require special permission)
    @Query("SELECT t FROM UserBrokerToken t WHERE t.userId = :userId AND t.broker = :broker")
    Optional<UserBrokerToken> findByUserIdAndBrokerForAdmin(@Param("userId") String userId, @Param("broker") String broker);
}
*/
