package com.example.algo.broker.auth;

import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface UserBrokerTokenRepo extends JpaRepository<UserBrokerToken, Long> {
  Optional<UserBrokerToken> findTopByUserIdAndBrokerOrderByUpdatedAtDesc(
      String userId, String broker);
}
