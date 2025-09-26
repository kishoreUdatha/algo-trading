package com.example.algo.broker.impl.zerodha;

import com.example.algo.broker.symbol.*;
import com.opencsv.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@RequiredArgsConstructor
public class KiteInstrumentCsvLoader {
  private final WebClient.Builder builder;
  private final InstrumentMapRepo repo;

  public int sync(String url) {
    var body = builder.build().get().uri(url).retrieve().bodyToMono(byte[].class).block();
    try (var reader =
        new CSVReader(
            new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8))) {
      String[] row;
      int cnt = 0;
      reader.readNext();
      List<InstrumentMap> batch = new ArrayList<>();
      while ((row = reader.readNext()) != null) {
        var token = row[0];
        var exch = row[1];
        var sym = row[2];
        batch.add(
            InstrumentMap.builder()
                .broker("zerodha")
                .exchange(exch)
                .symbol(sym)
                .token(token)
                .build());
        if (batch.size() % 1000 == 0) {
          repo.saveAll(batch);
          batch.clear();
        }
        cnt++;
      }
      if (!batch.isEmpty()) repo.saveAll(batch);
      return cnt;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
