package com.nuru.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuru.api.error.ApiError;
import com.nuru.auth.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stage 2 baseline: stateless API, health/docs public, everything else authenticated.
 * Stage 3 adds the JWT authentication filter and method security.
 */
@Configuration
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
      JwtAuthenticationFilter jwtFilter) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/actuator/health",
                "/actuator/health/**",
                "/actuator/info",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html")
            .permitAll()
            .requestMatchers(HttpMethod.POST,
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh")
            .permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint((request, response, authException) ->
                writeError(response, objectMapper, HttpStatus.UNAUTHORIZED,
                    "Authentication required", request.getRequestURI()))
            .accessDeniedHandler((request, response, accessDenied) ->
                writeError(response, objectMapper, HttpStatus.FORBIDDEN,
                    "Access denied", request.getRequestURI())))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable);
    return http.build();
  }

  private void writeError(HttpServletResponse response, ObjectMapper objectMapper,
      HttpStatus status, String message, String path) throws java.io.IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), ApiError.of(
        status.value(), status.getReasonPhrase(), message, path,
        MDC.get("correlationId"), null));
  }
}
