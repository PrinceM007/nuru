package com.nuru.service;

import com.nuru.ai.AiActionProposal;
import com.nuru.ai.AiActionProposalRepository;
import com.nuru.audit.AuditActions;
import com.nuru.audit.AuditService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists AI proposal failure outcomes in their own transaction: when a confirmed
 * action throws, the outer transaction must roll back (no partial writes), but the
 * FAILED status and its audit record must still be stored.
 */
@Service
public class ProposalOutcomeRecorder {

  private final AiActionProposalRepository proposals;
  private final AuditService auditService;

  public ProposalOutcomeRecorder(AiActionProposalRepository proposals,
      AuditService auditService) {
    this.proposals = proposals;
    this.auditService = auditService;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(UUID proposalId, UUID callerId, String toolName, String message) {
    proposals.findById(proposalId).ifPresent(proposal -> {
      proposal.setStatus(com.nuru.domain.ProposalStatus.FAILED);
      proposal.setDecidedAt(OffsetDateTime.now());
      proposal.setResultSummary(message.length() <= 2000 ? message : message.substring(0, 2000));
      auditService.record(callerId, proposal.getGroupId(),
          AuditActions.AI_ACTION_CONFIRMED, "AiActionProposal", proposal.getId().toString(),
          "FAILED", "tool=" + toolName);
    });
  }
}
