package com.example.algo.broker.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SecureUserBrokerTokenRepo extends JpaRepository<UserBrokerToken, Long> {



    /**
     * Find the latest valid token for a given user + broker.
     */
    Optional<UserBrokerToken> findTopByUserIdAndBrokerOrderByUpdatedAtDesc(String userId, String broker);

    /**
     * Optionally: find by tenant + broker.
     */
    Optional<UserBrokerToken> findTopByTenantIdAndBrokerOrderByUpdatedAtDesc(String tenantId, String broker);
}
