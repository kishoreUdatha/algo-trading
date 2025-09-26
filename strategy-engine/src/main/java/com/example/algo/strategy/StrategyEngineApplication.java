package com.example.algo.strategy;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;

@SpringBootApplication(scanBasePackages = {"com.example.algo.strategy", "com.example.algo.common"})
public class StrategyEngineApplication {
  public static void main(String[] args) {
    SpringApplication.run(StrategyEngineApplication.class, args);
  }
}
