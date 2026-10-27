package com.nuru.service;

import com.nuru.api.dto.IdentityDto;
import com.nuru.api.error.ForbiddenOperationException;
import com.nuru.api.error.ServiceUnavailableException;
import com.nuru.audit.AuditActions;
import com.nuru.audit.AuditService;
import com.nuru.domain.VerificationStatus;
import com.nuru.identity.VerificationState;
import com.nuru.identity.VerificationStateRepository;
import com.nuru.provider.identity.IdentityVerificationProvider;
import com.nuru.provider.identity.SandboxIdentityProvider;
import com.nuru.provider.identity.UnconfiguredIdentityProvider;
import com.nuru.users.UserConsent;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationService {

  private final IdentityVerificationProvider provider;
  private final VerificationStateRepository states;
  private final UserService userService;
  private final AuditService auditService;

  public VerificationService(UnconfiguredIdentityProvider unconfigured,
      SandboxIdentityProvider sandbox, VerificationStateRepository states,
      UserService userService, AuditService auditService,
      @Value("${nuru.identity.provider:}") String configured) {
    this.provider = "SANDBOX".equalsIgnoreCase(configured) ? sandbox : unconfigured;
    this.states = states;
    this.userService = userService;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public IdentityDto.Status status(UUID callerId) {
    if (!provider.isAvailable()) {
      return IdentityDto.Status.unavailable();
    }
    return states.findById(callerId)
        .map(IdentityDto.Status::from)
        .orElse(IdentityDto.Status.notStarted(provider.name()));
  }

  @Transactional
  public IdentityDto.Status start(UUID callerId) {
    requireConsent(callerId);
    requireProvider();
    IdentityVerificationProvider.VerificationResult result = provider.initiate(callerId);
    VerificationState state = states.findById(callerId)
        .orElseGet(() -> new VerificationState(callerId, VerificationStatus.NOT_STARTED));
    applyResult(state, result, provider.name());
    states.save(state);
    auditService.record(callerId, null, AuditActions.VERIFICATION_CHANGED, "VerificationState",
        callerId.toString(), "SUCCESS", "status=" + state.getStatus());
    return IdentityDto.Status.from(state);
  }

  @Transactional
  public IdentityDto.Status refresh(UUID callerId) {
    requireConsent(callerId);
    requireProvider();
    VerificationState state = states.findById(callerId).orElse(null);
    if (state == null || state.getProviderReference() == null) {
      throw new com.nuru.api.error.UnprocessableException(
          "No verification journey in progress; start verification first");
    }
    IdentityVerificationProvider.VerificationResult result =
        provider.refreshStatus(state.getProviderReference());
    if (result.status() == VerificationStatus.VERIFIED && result.expiresAt() != null
        && result.expiresAt().isBefore(OffsetDateTime.now())) {
      applyResult(state, new IdentityVerificationProvider.VerificationResult(
          VerificationStatus.EXPIRED, result.providerReference(), result.verifiedAt(),
          result.expiresAt(), "Verification expired"), provider.name());
    } else {
      applyResult(state, result, provider.name());
    }
    auditService.record(callerId, null, AuditActions.VERIFICATION_CHANGED, "VerificationState",
        callerId.toString(), "SUCCESS", "status=" + state.getStatus());
    return IdentityDto.Status.from(state);
  }

  private void applyResult(VerificationState state,
      IdentityVerificationProvider.VerificationResult result, String providerName) {
    state.setStatus(result.status());
    state.setProvider(providerName);
    state.setProviderReference(result.providerReference());
    state.setVerifiedAt(result.verifiedAt());
    state.setExpiresAt(result.expiresAt());
    state.setFailureReason(result.failureReason());
  }

  private void requireConsent(UUID callerId) {
    if (!userService.hasGranted(callerId, UserConsent.IDENTITY_VERIFICATION)) {
      throw new ForbiddenOperationException(
          "Identity verification consent is required before starting verification");
    }
  }

  private void requireProvider() {
    if (!provider.isAvailable()) {
      throw new ServiceUnavailableException(
          "Identity verification is not configured for this installation");
    }
  }
}
