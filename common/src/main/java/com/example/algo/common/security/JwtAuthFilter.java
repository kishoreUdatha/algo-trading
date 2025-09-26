/*
package com.example.algo.common.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthFilter implements Filter {
  private final String issuer = System.getenv().getOrDefault("JWT_ISSUER", "algo");
  private final String audience = System.getenv().getOrDefault("JWT_AUD", "algo-clients");
  private final String secret = System.getenv().getOrDefault("JWT_SECRET", "change-me-please");

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException, IOException, ServletException {
    var http = (HttpServletRequest) req;
    var header = http.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      var token = header.substring(7);
      try {
        var jwt = SignedJWT.parse(token);
        var verifier = new com.nimbusds.jose.crypto.MACVerifier(secret.getBytes());
        if (!jwt.verify(verifier)) {
          ((HttpServletResponse) res).sendError(401, "Bad signature");
          return;
        }
        var cl = jwt.getJWTClaimsSet();
        if (!issuer.equals(cl.getIssuer())) {
          ((HttpServletResponse) res).sendError(401, "Bad issuer");
          return;
        }
        if (!cl.getAudience().contains(audience)) {
          ((HttpServletResponse) res).sendError(401, "Bad audience");
          return;
        }
        if (cl.getExpirationTime().before(new Date())) {
          ((HttpServletResponse) res).sendError(401, "Expired");
          return;
        }
      } catch (Exception e) {
        ((HttpServletResponse) res).sendError(401, "Invalid token");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
*/
