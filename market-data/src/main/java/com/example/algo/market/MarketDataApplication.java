package com.example.algo.market;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;

@SpringBootApplication(
    scanBasePackages = {
      "com.example.algo.market",
      "com.example.algo.broker",
      "com.example.algo.common"
    })
public class MarketDataApplication {
  public static void main(String[] args) {
    SpringApplication.run(MarketDataApplication.class, args);
  }
}
