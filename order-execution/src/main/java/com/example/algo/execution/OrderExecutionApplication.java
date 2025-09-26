package com.example.algo.execution;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;

@SpringBootApplication(
    scanBasePackages = {
      "com.example.algo.execution",
      "com.example.algo.broker",
      "com.example.algo.common"
    })
public class OrderExecutionApplication {
  public static void main(String[] args) {
    SpringApplication.run(OrderExecutionApplication.class, args);
  }
}
