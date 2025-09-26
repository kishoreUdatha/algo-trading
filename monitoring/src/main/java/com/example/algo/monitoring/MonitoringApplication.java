package com.example.algo.monitoring;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;

@SpringBootApplication(
    scanBasePackages = {"com.example.algo.monitoring", "com.example.algo.common"})
public class MonitoringApplication {
  public static void main(String[] args) {
    SpringApplication.run(MonitoringApplication.class, args);
  }
}
