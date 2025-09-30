package com.example.algo.common.security;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;
import java.util.Set;

@Component
@Slf4j
public class MultiTenantJwtAuthFilter extends OncePerRequestFilter {

  @Value("${jwt.secret}")
  private String secret;

  private final UserSessionService userSessionService;

  public MultiTenantJwtAuthFilter(UserSessionService userSessionService) {
    this.userSessionService = userSessionService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String token = extractTokenFromRequest(request);

    if (token != null) {
      try {
        UserPrincipal user = validateTokenAndGetUser(token);

        // Validate session is still active
        if (!userSessionService.isSessionActive(user.getSessionId())) {
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired");
          return;
        }

        // Create authentication
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Log user access for audit
        logUserAccess(user, request);

      } catch (Exception e) {
        log.error("Authentication failed: {}", e.getMessage());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private String extractTokenFromRequest(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      return header.substring(7);
    }
    return null;
  }

  private UserPrincipal validateTokenAndGetUser(String token) throws Exception {
    SignedJWT jwt = SignedJWT.parse(token);

    // Verify signature
    JWSVerifier verifier = new MACVerifier(secret.getBytes());
    if (!jwt.verify(verifier)) {
      throw new SecurityException("Invalid token signature");
    }

    var claims = jwt.getJWTClaimsSet();

    // Check expiration
    if (claims.getExpirationTime().before(new Date())) {
      throw new SecurityException("Token expired");
    }

    // Extract user information
    String userId = claims.getSubject();
    String tenantId = claims.getStringClaim("tenant_id");
    String sessionId = claims.getStringClaim("session_id");

    @SuppressWarnings("unchecked")
    Set<String> roles = Set.copyOf(claims.getStringListClaim("roles"));

    @SuppressWarnings("unchecked")
    Set<String> permissions = Set.copyOf(claims.getStringListClaim("permissions"));

    @SuppressWarnings("unchecked")
    Set<String> authorizedBrokers = Set.copyOf(claims.getStringListClaim("brokers"));

    return new UserPrincipal(
        userId,
        claims.getStringClaim("username"),
        claims.getStringClaim("email"),
        roles,
        permissions,
        true,
        false,
        false,
        false,
        tenantId,
        sessionId,
        authorizedBrokers);
  }

  private void logUserAccess(UserPrincipal user, HttpServletRequest request) {
    log.info(
        "User access: userId={}, tenantId={}, sessionId={}, endpoint={}, ip={}",
        user.getUserId(),
        user.getTenantId(),
        user.getSessionId(),
        request.getRequestURI(),
        getClientIP(request));
  }

  private String getClientIP(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
