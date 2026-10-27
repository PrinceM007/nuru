package com.nuru.service;

import com.nuru.api.error.ConflictException;
import com.nuru.audit.AuditActions;
import com.nuru.audit.AuditService;
import com.nuru.auth.JwtService;
import com.nuru.auth.RefreshToken;
import com.nuru.auth.RefreshTokenRepository;
import com.nuru.auth.TokenHash;
import com.nuru.users.User;
import com.nuru.users.UserPreference;
import com.nuru.users.UserPreferenceRepository;
import com.nuru.users.UserRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository users;
  private final UserPreferenceRepository preferences;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuditService auditService;
  private final long refreshTokenDays;

  public AuthService(UserRepository users, UserPreferenceRepository preferences,
      RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
      JwtService jwtService, AuditService auditService,
      @Value("${nuru.jwt.refresh-token-days:14}") long refreshTokenDays) {
    this.users = users;
    this.preferences = preferences;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.auditService = auditService;
    this.refreshTokenDays = refreshTokenDays;
  }

  public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
  }

  @Transactional
  public TokenPair register(String email, String rawPassword, String displayName, String phone) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    if (users.existsByEmail(normalized)) {
      throw new ConflictException("An account with this email already exists");
    }
    User user = users.save(new User(normalized, passwordEncoder.encode(rawPassword),
        displayName.trim(), phone));
    preferences.save(new UserPreference(user.getId()));
    auditService.record(user.getId(), null, AuditActions.USER_REGISTERED, "User",
        user.getId().toString(), "SUCCESS", null);
    return issueTokens(user.getId());
  }

  @Transactional
  public TokenPair login(String email, String rawPassword) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    User user = users.findByEmail(normalized)
        .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
        .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
    auditService.record(user.getId(), null, AuditActions.USER_SIGNED_IN, "User",
        user.getId().toString(), "SUCCESS", null);
    return issueTokens(user.getId());
  }

  @Transactional
  public TokenPair refresh(String refreshToken) {
    RefreshToken stored = refreshTokens.findByTokenHash(TokenHash.sha256Hex(refreshToken))
        .filter(RefreshToken::isUsable)
        .orElseThrow(() -> new BadCredentialsException("Invalid or expired session"));
    stored.setRevokedAt(OffsetDateTime.now());
    return issueTokens(stored.getUserId());
  }

  @Transactional
  public void logout(UUID userId, String refreshToken) {
    refreshTokens.findByTokenHash(TokenHash.sha256Hex(refreshToken))
        .filter(t -> t.getUserId().equals(userId) && t.getRevokedAt() == null)
        .ifPresent(t -> t.setRevokedAt(OffsetDateTime.now()));
    auditService.record(userId, null, AuditActions.USER_SIGNED_OUT, "User",
        userId.toString(), "SUCCESS", null);
  }

  private TokenPair issueTokens(UUID userId) {
    String refresh = TokenHash.mintOpaqueToken();
    refreshTokens.save(new RefreshToken(userId, TokenHash.sha256Hex(refresh),
        OffsetDateTime.now().plusDays(refreshTokenDays)));
    return new TokenPair(jwtService.createAccessToken(userId), refresh,
        jwtService.getAccessMinutes() * 60);
  }
}
