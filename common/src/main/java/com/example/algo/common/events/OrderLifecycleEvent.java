package com.example.algo.common.events;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderLifecycleEvent {
  String orderId;
  String status;
  String details;
  long ts;
}
