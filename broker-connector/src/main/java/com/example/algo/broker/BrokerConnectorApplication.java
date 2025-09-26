package com.example.algo.broker;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;

@SpringBootApplication(scanBasePackages = {"com.example.algo.broker", "com.example.algo.common"})
public class BrokerConnectorApplication {
  public static void main(String[] args) {
    SpringApplication.run(BrokerConnectorApplication.class, args);
  }
}
