package com.example.algo.monitoring.web;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/public/auth")
public class AuthController {
  @PostMapping("/token")
  public Map<String, String> token(@RequestParam String user) {
    try {
      var now = Instant.now();
      var secret = System.getenv().getOrDefault("JWT_SECRET", "change-me-please");
      var claims =
          new JWTClaimsSet.Builder()
              .issuer("algo")
              .audience("algo-clients")
              .subject(user)
              .issueTime(Date.from(now))
              .expirationTime(Date.from(now.plusSeconds(3600)))
              .build();
      var jws = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      jws.sign(new MACSigner(secret.getBytes()));
      return Map.of("token", jws.serialize());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
