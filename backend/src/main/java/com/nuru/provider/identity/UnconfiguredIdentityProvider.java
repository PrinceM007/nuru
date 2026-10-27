package com.nuru.provider.identity;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Default: no verification provider configured — verification is unavailable. */
@Component
public class UnconfiguredIdentityProvider implements IdentityVerificationProvider {

  @Override
  public String name() {
    return "UNCONFIGURED";
  }

  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public VerificationResult initiate(UUID userId) {
    throw new IllegalStateException("No identity verification provider is configured");
  }

  @Override
  public VerificationResult refreshStatus(String providerReference) {
    throw new IllegalStateException("No identity verification provider is configured");
  }
}
