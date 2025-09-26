package com.example.algo.broker.web;

import com.example.algo.broker.core.*;
import com.example.algo.common.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.*;

@RestController
@RequestMapping("/api/v1/brokers")
@RequiredArgsConstructor
public class BrokerController {
  private final BrokerRouter router;

  @GetMapping
  public List<String> list() {
    return router.active().stream().map(BrokerClient::name).toList();
  }

  @GetMapping("/{name}/instruments")
  public Mono<List<Instrument>> instruments(@PathVariable String name) {
    return router.byName(name).fetchInstrumentsOnce();
  }
}
