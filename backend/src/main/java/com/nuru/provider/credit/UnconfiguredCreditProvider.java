package com.nuru.provider.credit;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Default: no credit provider configured. */
@Component
public class UnconfiguredCreditProvider implements CreditScoreProvider {

  @Override
  public String name() {
    return "UNCONFIGURED";
  }

  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public CreditEstimate estimate(UUID groupId, UUID memberUserId) {
    throw new IllegalStateException("No credit provider is configured");
  }
}
