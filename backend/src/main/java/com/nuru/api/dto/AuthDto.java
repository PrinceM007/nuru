package com.nuru.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDto {

  private AuthDto() {
  }

  public record RegisterRequest(
      @Email @NotBlank @Size(max = 320) String email,
      @NotBlank @Size(min = 8, max = 128) String password,
      @NotBlank @Size(min = 1, max = 120) String displayName,
      @Size(max = 40) String phone) {
  }

  public record LoginRequest(
      @Email @NotBlank String email,
      @NotBlank String password) {
  }

  public record RefreshRequest(@NotBlank String refreshToken) {
  }

  public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds) {
  }
}
