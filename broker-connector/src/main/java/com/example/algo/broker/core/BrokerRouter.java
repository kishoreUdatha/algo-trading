package com.example.algo.broker.core;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
public class BrokerRouter {
  private final List<BrokerClient> clients;

  public BrokerClient byName(String name) {
    return clients.stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst().orElseThrow();
  }

  public List<BrokerClient> active() {
    return clients;
  }
}
