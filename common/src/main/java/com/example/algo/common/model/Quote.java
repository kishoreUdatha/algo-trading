package com.example.algo.common.model;
import lombok.*;
@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class Quote { String symbol; double ltp; long ts; }
