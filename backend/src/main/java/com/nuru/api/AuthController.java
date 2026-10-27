package com.nuru.api;

import com.nuru.api.dto.AuthDto;
import com.nuru.auth.JwtAuthenticationFilter;
import com.nuru.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Register a new user account")
  public AuthDto.TokenResponse register(@Valid @RequestBody AuthDto.RegisterRequest request) {
    return toResponse(authService.register(request.email(), request.password(),
        request.displayName(), request.phone()));
  }

  @PostMapping("/login")
  @Operation(summary = "Sign in with email and password")
  public AuthDto.TokenResponse login(@Valid @RequestBody AuthDto.LoginRequest request) {
    return toResponse(authService.login(request.email(), request.password()));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Rotate a refresh token for a new token pair")
  public AuthDto.TokenResponse refresh(@Valid @RequestBody AuthDto.RefreshRequest request) {
    return toResponse(authService.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Sign out by revoking the given refresh token")
  public void logout(@Valid @RequestBody AuthDto.RefreshRequest request) {
    authService.logout(JwtAuthenticationFilter.principal(), request.refreshToken());
  }

  private AuthDto.TokenResponse toResponse(AuthService.TokenPair pair) {
    return new AuthDto.TokenResponse(pair.accessToken(), pair.refreshToken(),
        pair.expiresInSeconds());
  }
}
