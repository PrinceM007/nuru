package com.nuru.provider.identity;

import com.nuru.domain.VerificationStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Identity verification provider. Implementations confirm a person's identity
 * out of band; Nuru stores only the status plus an opaque provider reference —
 * never document images or document contents.
 */
public interface IdentityVerificationProvider {

  String name();

  boolean isAvailable();

  VerificationResult initiate(UUID userId);

  VerificationResult refreshStatus(String providerReference);

  record VerificationResult(VerificationStatus status, String providerReference,
      OffsetDateTime verifiedAt, OffsetDateTime expiresAt, String failureReason) {
  }
}
