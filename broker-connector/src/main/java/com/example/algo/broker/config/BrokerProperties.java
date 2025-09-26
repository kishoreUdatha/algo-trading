package com.example.algo.broker.config;

import lombok.*;
import org.springframework.boot.context.properties.*;
import org.springframework.context.annotation.Configuration;
import java.util.*;

@Data
@Configuration
@ConfigurationProperties(prefix = "brokers")
public class BrokerProperties {
  private Map<String, Entry> registry = new HashMap<>();

  @Data
  public static class Entry {
    String baseUrl;
    String apiKey;
    String secret;
    boolean active;
  }
}
