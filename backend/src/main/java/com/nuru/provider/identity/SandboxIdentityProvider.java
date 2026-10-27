package com.nuru.provider.identity;

import com.nuru.domain.VerificationStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Sandbox provider for development and integration tests. Simulates a
 * verification journey (PENDING → VERIFIED). Clearly labeled — must never be
 * mistaken for real identity verification.
 */
@Component
public class SandboxIdentityProvider implements IdentityVerificationProvider {

  @Override
  public String name() {
    return "SANDBOX";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public VerificationResult initiate(UUID userId) {
    return new VerificationResult(VerificationStatus.PENDING,
        "SANDBOX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), null, null,
        null);
  }

  @Override
  public VerificationResult refreshStatus(String providerReference) {
    OffsetDateTime now = OffsetDateTime.now();
    return new VerificationResult(VerificationStatus.VERIFIED, providerReference, now,
        now.plusYears(1), null);
  }
}
