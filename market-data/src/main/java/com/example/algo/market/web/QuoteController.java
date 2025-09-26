package com.example.algo.market.web;

import com.example.algo.common.events.MarketTickEvent;
import com.example.algo.common.model.Quote;
import com.example.algo.market.messaging.MarketTickPublisher;
import com.example.algo.market.service.MarketDataStreamer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import java.util.List;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class QuoteController {
  private final MarketDataStreamer streamer;
  private final MarketTickPublisher publisher;

  @GetMapping("/{broker}/stream")
  public Flux<Quote> stream(@PathVariable String broker,
          @RequestParam List<String> symbols) {
    return streamer.stream(broker, symbols)
        .doOnNext(
            q -> publisher.publish(new MarketTickEvent(q.getSymbol(), q.getLtp(), q.getTs())));
  }
}
