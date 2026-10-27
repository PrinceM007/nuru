package com.nuru.provider.payment;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Selects the configured payment adapter. Default is MANUAL bookkeeping. */
@Service
public class PaymentService {

  private final PaymentProvider provider;

  public PaymentService(ManualPaymentProvider manual, SandboxPaymentProvider sandbox,
      @Value("${nuru.payments.mode:MANUAL}") String mode) {
    this.provider = "SANDBOX".equalsIgnoreCase(mode) ? sandbox : manual;
  }

  public ProviderPayment recordInbound(UUID groupId, UUID memberUserId, BigDecimal amount,
      String currency, String narrative) {
    return provider.recordInbound(groupId, memberUserId, amount, currency, narrative);
  }

  public String providerName() {
    return provider.name();
  }
}
