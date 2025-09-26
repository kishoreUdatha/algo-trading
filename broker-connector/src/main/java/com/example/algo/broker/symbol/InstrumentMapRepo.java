package com.example.algo.broker.symbol;

import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface InstrumentMapRepo extends JpaRepository<InstrumentMap, Long> {
  Optional<InstrumentMap> findByBrokerAndExchangeAndSymbol(
      String broker, String exchange, String symbol);
}
