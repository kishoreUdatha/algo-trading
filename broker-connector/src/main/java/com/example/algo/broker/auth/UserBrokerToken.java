package com.example.algo.broker.auth;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity
@Table(name = "user_broker_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBrokerToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false)
  String userId;

  @Column(nullable = false)
  String broker;

  @Column(length = 4096)
  String accessToken;

  @Column(length = 4096)
  String refreshToken;

  Instant accessTokenExpiry;
  Instant refreshTokenExpiry;
  Instant updatedAt;
}
