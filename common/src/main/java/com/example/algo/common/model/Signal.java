package com.example.algo.common.model;
import com.example.algo.common.model.enums.*;import lombok.*;
@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class Signal { String strategy; String symbol; OrderSide side; double price; long ts; }
