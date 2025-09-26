package com.example.algo.common.events;
import lombok.*;
@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class MarketTickEvent { String symbol; double ltp; long ts; }
