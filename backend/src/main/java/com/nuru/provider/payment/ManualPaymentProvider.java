package com.nuru.provider.payment;

import com.nuru.domain.RecordedVia;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Manual bookkeeping adapter: a human records that money changed hands offline
 * (cash, bank transfer done outside Nuru). The reference is only a label.
 */
@Component
public class ManualPaymentProvider implements PaymentProvider {

  private final AtomicLong sequence = new AtomicLong();

  @Override
  public String name() {
    return "MANUAL";
  }

  @Override
  public RecordedVia recordedVia() {
    return RecordedVia.MANUAL;
  }

  @Override
  public ProviderPayment recordInbound(UUID groupId, UUID memberUserId, BigDecimal amount,
      String currency, String narrative) {
    String reference = "MANUAL-" + OffsetDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        + "-" + sequence.incrementAndGet();
    return new ProviderPayment(RecordedVia.MANUAL, reference,
        "Manually recorded — verify funds outside Nuru");
  }
}
