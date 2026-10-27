package com.nuru.provider.credit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Optional credit information. Scores are estimates with published factors —
 * never verdicts. Nothing in Nuru automatically denies loans or makes
 * consequential decisions from a score; loan approval ignores it entirely.
 */
public interface CreditScoreProvider {

  String name();

  boolean isAvailable();

  CreditEstimate estimate(UUID groupId, UUID memberUserId);

  record Factor(String name, String value, double weight) {
  }

  record CreditEstimate(boolean insufficientHistory, Integer score, List<Factor> factors,
      LocalDate dataPeriodStart, LocalDate dataPeriodEnd, OffsetDateTime estimatedAt,
      String label) {
  }
}
