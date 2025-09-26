package com.example.algo.broker.symbol;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "instrument_map",
    uniqueConstraints = @UniqueConstraint(columnNames = {"broker", "exchange", "symbol"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentMap {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false)
  String broker;

  @Column(nullable = false)
  String exchange;

  @Column(nullable = false)
  String symbol;

  @Column(nullable = false)
  String token;
}
