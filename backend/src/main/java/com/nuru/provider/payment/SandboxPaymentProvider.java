package com.nuru.provider.payment;

import com.nuru.domain.RecordedVia;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Sandbox adapter for development and demos. Simulates provider acceptance with
 * fake references. Must never be mistaken for real payment confirmation.
 */
@Component
public class SandboxPaymentProvider implements PaymentProvider {

  @Override
  public String name() {
    return "SANDBOX";
  }

  @Override
  public RecordedVia recordedVia() {
    return RecordedVia.SANDBOX;
  }

  @Override
  public ProviderPayment recordInbound(UUID groupId, UUID memberUserId, BigDecimal amount,
      String currency, String narrative) {
    String reference = "SANDBOX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    return new ProviderPayment(RecordedVia.SANDBOX, reference,
        "Sandbox simulation — no real funds moved");
  }
}
