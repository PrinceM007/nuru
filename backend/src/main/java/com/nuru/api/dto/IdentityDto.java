package com.nuru.api.dto;

import com.nuru.identity.VerificationState;
import com.nuru.provider.credit.CreditScoreProvider;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class IdentityDto {

  private IdentityDto() {
  }

  public record Status(String status, String provider, String providerReference,
      OffsetDateTime verifiedAt, OffsetDateTime expiresAt, String failureReason,
      OffsetDateTime updatedAt) {
    public static Status from(VerificationState state) {
      return new Status(state.getStatus().name(), state.getProvider(),
          state.getProviderReference(), state.getVerifiedAt(), state.getExpiresAt(),
          state.getFailureReason(), state.getUpdatedAt());
    }

    public static Status unavailable() {
      return new Status("UNAVAILABLE", "UNCONFIGURED", null, null, null,
          "Identity verification is not configured for this installation", null);
    }

    public static Status notStarted(String provider) {
      return new Status("NOT_STARTED", provider, null, null, null, null, null);
    }
  }

  public record CreditFactor(String name, String value, double weight) {
  }

  public record CreditEstimate(String provider, UUID memberUserId, boolean insufficientHistory,
      Integer score, List<CreditFactor> factors, LocalDate dataPeriodStart,
      LocalDate dataPeriodEnd, OffsetDateTime estimatedAt, String label) {
    public static CreditEstimate from(String provider, UUID member,
        CreditScoreProvider.CreditEstimate estimate) {
      return new CreditEstimate(provider, member, estimate.insufficientHistory(),
          estimate.score(),
          estimate.factors().stream()
              .map(f -> new CreditFactor(f.name(), f.value(), f.weight())).toList(),
          estimate.dataPeriodStart(), estimate.dataPeriodEnd(), estimate.estimatedAt(),
          estimate.label());
    }
  }
}
