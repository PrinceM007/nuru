package com.nuru.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Stateless bearer authentication. Invalid tokens stay anonymous → 401 from the entry point. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      jwtService.validateAndExtractUserId(header.substring(7).trim()).ifPresent(userId ->
          SecurityContextHolder.getContext().setAuthentication(
              new UsernamePasswordAuthenticationToken(userId, null, List.of())));
    }
    chain.doFilter(request, response);
  }

  /** For programmatic lookups that expect a UUID-typed principal. */
  public static UUID principal() {
    Object principal = SecurityContextHolder.getContext().getAuthentication() != null
        ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
        : null;
    if (principal instanceof UUID uuid) {
      return uuid;
    }
    throw new org.springframework.security.authentication.BadCredentialsException(
        "Authentication required");
  }
}
