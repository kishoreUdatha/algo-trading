package com.example.algo.broker.symbol;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class InstrumentResolver {
  private final InstrumentMapRepo repo;
  private final StringRedisTemplate rt;

  private String key(String b, String e, String s) {
    return "symtok:" + b + ":" + e + ":" + s;
  }

  public String token(String broker, String exchange, String symbol) {
    var k = key(broker, exchange, symbol);
    var cached = rt.opsForValue().get(k);
    if (cached != null) return cached;
    var tok =
        repo.findByBrokerAndExchangeAndSymbol(broker, exchange, symbol).orElseThrow().getToken();
    rt.opsForValue().set(k, tok, Duration.ofHours(12));
    return tok;
  }
}
