package com.example.algo.common.model;
import lombok.*;
@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class Instrument { String symbol; String segment; double tickSize; int lotSize; }
