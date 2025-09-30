package com.example.algo.broker;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.context.annotation.*;

@SpringBootApplication(scanBasePackages = {"com.example.algo.broker", "com.example.algo.common"})
@ComponentScan(
    basePackages = {"com.example.algo.broker", "com.example.algo.common"},
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.example\\.algo\\.common\\.security\\.CustomUserDetailsService"))
public class BrokerConnectorApplication {
  public static void main(String[] args) {
    SpringApplication.run(BrokerConnectorApplication.class, args);
  }
}
