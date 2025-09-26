package com.example.algo.monitoring.web;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class BrokersResource {
  @GetMapping("/brokers")
  public List<String> brokers() {
    return List.of("upstox", "zerodha");
  }
}
