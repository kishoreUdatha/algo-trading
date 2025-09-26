package com.example.algo.market.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;

@Configuration
public class KafkaTopics {
  @Bean
  NewTopic ticks() {
    return new NewTopic("tp.market.ticks", 3, (short) 1);
  }
}
