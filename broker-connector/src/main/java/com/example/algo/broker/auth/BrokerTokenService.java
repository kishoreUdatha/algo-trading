package com.example.algo.broker.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BrokerTokenService {
  private final UserBrokerTokenRepo repo;
  private final Map<String, TokenRefresher> refreshers = new HashMap<>();

  public Optional<UserBrokerToken> current(String userId, String broker) {
    return repo.findTopByUserIdAndBrokerOrderByUpdatedAtDesc(userId, broker);
  }

  public String ensureValidAccessToken(String userId, String broker) {
    var tok =
        current(userId, broker).orElseThrow(() -> new IllegalStateException("No token for " + broker));
    if (tok.getAccessTokenExpiry() != null
        && tok.getAccessTokenExpiry().isBefore(Instant.now().plusSeconds(30))) {
      var ref = refreshers.get(broker);
      if (ref != null && tok.getRefreshToken() != null) {
        tok = ref.refresh(tok);
      }
    }
    return tok.getAccessToken();
  }

  public UserBrokerToken save(
      String userId,
      String broker,
      String access,
      Instant accessExp,
      String refresh,
      Instant refreshExp) {
    var t =
        UserBrokerToken.builder()
            .userId(userId)
            .broker(broker)
            .accessToken(access)
            .accessTokenExpiry(accessExp)
            .refreshToken(refresh)
            .refreshTokenExpiry(refreshExp)
            .updatedAt(Instant.now())
            .build();
    return repo.save(t);
  }

  public interface TokenRefresher {
    UserBrokerToken refresh(UserBrokerToken existing);
  }
}
