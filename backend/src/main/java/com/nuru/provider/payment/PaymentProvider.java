package com.nuru.provider.payment;

import com.nuru.domain.RecordedVia;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment provider abstraction. No implementation here moves real money:
 * live providers are a future integration behind this interface.
 */
public interface PaymentProvider {

  /** Stable provider name used in logs and audit details (never a secret). */
  String name();

  RecordedVia recordedVia();

  /**
   * Records an inbound (member → group) payment instruction. The returned reference
   * is a bookkeeping label, NOT proof that funds moved.
   */
  ProviderPayment recordInbound(UUID groupId, UUID memberUserId, BigDecimal amount,
      String currency, String narrative);

  /** False for every bundled adapter: none of them confirms real fund movement. */
  default boolean confirmsRealMovement() {
    return false;
  }
}
