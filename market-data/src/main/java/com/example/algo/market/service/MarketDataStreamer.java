package com.example.algo.market.service;

import com.example.algo.broker.core.BrokerRouter;
import com.example.algo.common.model.Quote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MarketDataStreamer {
  private final BrokerRouter router;

  public Flux<Quote> stream(String broker, Collection<String> symbols) {
    return router.byName(broker).getMarketData(symbols);
  }
}
