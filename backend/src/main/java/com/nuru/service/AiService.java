package com.nuru.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuru.ai.AiActionProposal;
import com.nuru.ai.AiActionProposalRepository;
import com.nuru.ai.AiConversation;
import com.nuru.ai.AiConversationRepository;
import com.nuru.ai.AiMessage;
import com.nuru.ai.AiMessageRepository;
import com.nuru.ai.AiProvider;
import com.nuru.ai.AiProviderException;
import com.nuru.ai.AiTool;
import com.nuru.ai.AiToolRegistry;
import com.nuru.ai.AiToolResult;
import com.nuru.api.dto.AiDto;
import com.nuru.api.error.ForbiddenOperationException;
import com.nuru.api.error.ResourceNotFoundException;
import com.nuru.api.error.ServiceUnavailableException;
import com.nuru.api.error.UnprocessableException;
import com.nuru.audit.AuditActions;
import com.nuru.audit.AuditService;
import com.nuru.domain.AiMessageRole;
import com.nuru.domain.ProposalStatus;
import com.nuru.users.UserConsent;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversational assistant orchestration. The provider only plans (text + tool
 * calls); this service validates every call, executes reads through application
 * services, and routes state-changing calls through explicit user confirmation.
 */
@Service
public class AiService {

  private static final Logger log = LoggerFactory.getLogger(AiService.class);
  private static final int MAX_USER_CHARS = 4000;
  private static final int MAX_STORED_TOOL_CHARS = 4000;

  private final AiProvider provider;
  private final AiToolRegistry registry;
  private final AiConversationRepository conversations;
  private final AiMessageRepository messages;
  private final AiActionProposalRepository proposals;
  private final GroupAuth groupAuth;
  private final UserService userService;
  private final AuditService auditService;
  private final ProposalOutcomeRecorder outcomeRecorder;
  private final ObjectMapper objectMapper;
  private final int maxToolSteps;
  private final long proposalExpiryHours;

  public AiService(AiProvider provider, AiToolRegistry registry,
      AiConversationRepository conversations, AiMessageRepository messages,
      AiActionProposalRepository proposals, GroupAuth groupAuth, UserService userService,
      AuditService auditService, ProposalOutcomeRecorder outcomeRecorder,
      ObjectMapper objectMapper,
      @Value("${nuru.ai.max-tool-steps:4}") int maxToolSteps,
      @Value("${nuru.ai.proposal-expiry-hours:24}") long proposalExpiryHours) {
    this.provider = provider;
    this.registry = registry;
    this.conversations = conversations;
    this.messages = messages;
    this.proposals = proposals;
    this.groupAuth = groupAuth;
    this.userService = userService;
    this.auditService = auditService;
    this.outcomeRecorder = outcomeRecorder;
    this.objectMapper = objectMapper;
    this.maxToolSteps = maxToolSteps;
    this.proposalExpiryHours = proposalExpiryHours;
  }

  public AiDto.Status status() {
    return new AiDto.Status(provider.name(), provider.isAvailable(), provider.statusDetail());
  }

  @Transactional
  public AiDto.Conversation createConversation(UUID callerId, UUID groupId, String title) {
    if (groupId != null) {
      groupAuth.requireMembership(groupId, callerId);
    }
    AiConversation conversation = conversations.save(new AiConversation(callerId, groupId,
        title == null || title.isBlank() ? null : title.trim()));
    return AiDto.Conversation.from(conversation);
  }

  @Transactional(readOnly = true)
  public Page<AiDto.Conversation> listConversations(UUID callerId, Pageable pageable) {
    return conversations.findByUserIdOrderByUpdatedAtDesc(callerId, pageable)
        .map(AiDto.Conversation::from);
  }

  @Transactional(readOnly = true)
  public AiDto.ConversationDetail getConversation(UUID callerId, UUID conversationId) {
    AiConversation conversation = ownConversation(callerId, conversationId);
    List<AiDto.Message> history = messages
        .findByConversationIdOrderById(conversationId).stream().map(AiDto.Message::from)
        .toList();
    List<AiDto.Proposal> proposalDtos = proposals
        .findByConversationIdOrderByCreatedAtDesc(conversationId).stream()
        .map(this::toProposalDto).toList();
    return AiDto.ConversationDetail.from(conversation, history, proposalDtos);
  }

  @Transactional
  public void deleteConversation(UUID callerId, UUID conversationId) {
    conversations.delete(ownConversation(callerId, conversationId));
  }

  @Transactional
  public AiDto.ChatResponse sendMessage(UUID callerId, UUID conversationId, String text) {
    AiConversation conversation = ownConversation(callerId, conversationId);
    requireAiConsent(callerId);
    requireProvider();
    if (text == null || text.isBlank()) {
      throw new UnprocessableException("Message text is required");
    }
    if (text.length() > MAX_USER_CHARS) {
      throw new UnprocessableException(
          "Message must be at most " + MAX_USER_CHARS + " characters");
    }
    messages.save(new AiMessage(conversationId, AiMessageRole.USER, text.trim()));

    List<AiDto.ExecutedTool> executed = new ArrayList<>();
    List<AiDto.Proposal> createdProposals = new ArrayList<>();
    String assistantText = runAgentLoop(callerId, conversation, text.trim(), executed,
        createdProposals);

    messages.save(new AiMessage(conversationId, AiMessageRole.ASSISTANT, assistantText));
    conversation.touch();
    return new AiDto.ChatResponse(assistantText, executed, createdProposals);
  }

  private String runAgentLoop(UUID callerId, AiConversation conversation, String userText,
      List<AiDto.ExecutedTool> executed, List<AiDto.Proposal> createdProposals) {
    UUID groupId = conversation.getGroupId();
    String currentText = userText;
    for (int step = 0; step < maxToolSteps; step++) {
      AiProvider.AiPlannerResponse plan = plan(callerId, conversation, currentText);
      if (plan.refusal()) {
        return "The AI provider refused to answer. " + safeText(plan.text());
      }
      if (plan.toolCalls() == null || plan.toolCalls().isEmpty()) {
        return safeText(plan.text());
      }
      boolean madeProposal = false;
      String planText = safeText(plan.text());
      for (AiProvider.PlannedCall call : plan.toolCalls()) {
        AiTool tool;
        try {
          tool = registry.requireTool(call.tool());
        } catch (UnprocessableException e) {
          recordToolMessage(conversation.getId(), call.tool(), "Unknown tool requested.");
          return "I tried to use an unknown capability ('" + call.tool()
              + "'). Please rephrase your request.";
        }
        Map<String, Object> args;
        try {
          args = registry.validatedArgs(tool, call.arguments());
        } catch (UnprocessableException e) {
          recordToolMessage(conversation.getId(), tool.name(), "Invalid arguments: "
              + e.getMessage());
          return "I couldn't run '" + tool.name() + "': " + e.getMessage()
              + " Please provide the missing details.";
        }
        UUID effectiveGroup = effectiveGroup(groupId, call.arguments());
        if (tool.requiresConfirmation()) {
          AiActionProposal proposal = proposals.save(new AiActionProposal(
              conversation.getId(), callerId, effectiveGroup, tool.name(), toJson(args),
              OffsetDateTime.now().plusHours(proposalExpiryHours)));
          auditService.record(callerId, effectiveGroup, AuditActions.AI_ACTION_PROPOSED,
              "AiActionProposal", proposal.getId().toString(), "SUCCESS",
              "tool=" + tool.name());
          createdProposals.add(toProposalDto(proposal));
          madeProposal = true;
        } else {
          try {
            AiToolResult result = tool.execute(callerId, effectiveGroup, args);
            recordToolMessage(conversation.getId(), tool.name(), result.summary());
            executed.add(new AiDto.ExecutedTool(tool.name(), result.summary(),
                result.facts()));
          } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Tool execution failed."
                : e.getMessage();
            recordToolMessage(conversation.getId(), tool.name(), "Failed: " + message);
            return "I tried '" + tool.name() + "' but it failed: " + message;
          }
        }
      }
      if (madeProposal) {
        StringBuilder text = new StringBuilder(planText.isEmpty()
            ? "I've prepared the following proposal(s)."
            : planText);
        for (AiDto.Proposal proposal : createdProposals) {
          text.append("\n\n- ").append(proposal.description());
        }
        text.append("\n\nReview them and confirm to proceed — nothing has changed yet.");
        return text.toString();
      }
      currentText = "Summarize the tool results for the user in one or two sentences.";
    }
    return "I ran several steps without finishing. Please try a narrower request.";
  }

  private AiProvider.AiPlannerResponse plan(UUID callerId, AiConversation conversation,
      String userText) {
    List<AiProvider.HistoryMessage> history = messages
        .findByConversationIdOrderById(conversation.getId()).stream()
        .map(m -> new AiProvider.HistoryMessage(m.getRole().name(), m.getContent()))
        .toList();
    try {
      return provider.plan(new AiProvider.AiPlannerRequest(callerId, conversation.getGroupId(),
          history, userText, registry.specs()));
    } catch (AiProviderException e) {
      throw new ServiceUnavailableException(switch (e.kind()) {
        case TIMEOUT -> "The AI provider timed out. Please try again.";
        case REFUSAL -> "The AI provider refused to answer.";
        case TRANSPORT -> "The AI provider is unreachable right now.";
        case INVALID_RESPONSE -> "The AI provider returned an unusable response.";
      });
    }
  }

  @Transactional(readOnly = true)
  public List<AiDto.Proposal> listProposals(UUID callerId, String status) {
    ProposalStatus filter = status == null ? ProposalStatus.PENDING : parseStatus(status);
    return proposals.findByUserIdAndStatusOrderByCreatedAtDesc(callerId, filter).stream()
        .map(this::toProposalDto).toList();
  }

  @Transactional
  public AiDto.Proposal confirmProposal(UUID callerId, UUID proposalId) {
    AiActionProposal proposal = ownProposal(callerId, proposalId);
    requireAiConsent(callerId);
    if (!proposal.isDecidable()) {
      if (proposal.getStatus() == ProposalStatus.PENDING) {
        proposal.setStatus(ProposalStatus.EXPIRED);
        proposal.setDecidedAt(OffsetDateTime.now());
      }
      throw new UnprocessableException("This proposal is no longer pending");
    }
    AiTool tool = registry.requireTool(proposal.getToolName());
    Map<String, Object> args = registry.validatedArgs(tool, fromJson(proposal.getArgumentsJson()));
    try {
      AiToolResult result = tool.execute(callerId, proposal.getGroupId(), args);
      proposal.setStatus(ProposalStatus.CONFIRMED);
      proposal.setDecidedAt(OffsetDateTime.now());
      proposal.setResultSummary(truncate(result.summary(), 2000));
      auditService.record(callerId, proposal.getGroupId(),
          AuditActions.AI_ACTION_CONFIRMED, "AiActionProposal", proposal.getId().toString(),
          "SUCCESS", "tool=" + tool.name());
      messages.save(new AiMessage(proposal.getConversationId(), AiMessageRole.ASSISTANT,
          "Confirmed and completed '" + tool.name() + "': " + result.summary()));
      return toProposalDto(proposal);
    } catch (RuntimeException e) {
      // Recorded in a separate transaction: the outer one rolls back by design.
      String message = e.getMessage() == null ? "Action failed." : e.getMessage();
      outcomeRecorder.markFailed(proposal.getId(), callerId, tool.name(), message);
      throw e;
    }
  }

  @Transactional
  public AiDto.Proposal rejectProposal(UUID callerId, UUID proposalId) {
    AiActionProposal proposal = ownProposal(callerId, proposalId);
    if (proposal.getStatus() != ProposalStatus.PENDING) {
      throw new UnprocessableException("Only pending proposals can be rejected");
    }
    proposal.setStatus(ProposalStatus.REJECTED);
    proposal.setDecidedAt(OffsetDateTime.now());
    auditService.record(callerId, proposal.getGroupId(), AuditActions.AI_ACTION_REJECTED,
        "AiActionProposal", proposal.getId().toString(), "SUCCESS",
        "tool=" + proposal.getToolName());
    return toProposalDto(proposal);
  }

  private void requireAiConsent(UUID callerId) {
    if (!userService.hasGranted(callerId, UserConsent.AI_ASSISTANCE)) {
      throw new ForbiddenOperationException(
          "AI assistance consent is required before using the assistant");
    }
  }

  private void requireProvider() {
    if (!provider.isAvailable()) {
      throw new ServiceUnavailableException(
          "AI provider is not configured. " + provider.statusDetail());
    }
  }

  private AiConversation ownConversation(UUID callerId, UUID conversationId) {
    AiConversation conversation = conversations.findById(conversationId)
        .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
    if (!conversation.getUserId().equals(callerId)) {
      throw new ResourceNotFoundException("Conversation not found");
    }
    return conversation;
  }

  private AiActionProposal ownProposal(UUID callerId, UUID proposalId) {
    AiActionProposal proposal = proposals.findById(proposalId)
        .orElseThrow(() -> new ResourceNotFoundException("Proposal not found"));
    if (!proposal.getUserId().equals(callerId)) {
      throw new ResourceNotFoundException("Proposal not found");
    }
    return proposal;
  }

  private ProposalStatus parseStatus(String status) {
    try {
      return ProposalStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException("Unknown proposal status: " + status);
    }
  }

  private UUID effectiveGroup(UUID conversationGroup, Map<String, Object> rawArgs) {
    if (conversationGroup != null) {
      return conversationGroup;
    }
    if (rawArgs != null && rawArgs.get("groupId") instanceof String text) {
      try {
        return UUID.fromString(text.trim());
      } catch (IllegalArgumentException e) {
        throw new UnprocessableException("Tool argument 'groupId' is not a valid id");
      }
    }
    return null;
  }

  private void recordToolMessage(UUID conversationId, String tool, String summary) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("tool", tool);
    entry.put("summary", summary);
    messages.save(new AiMessage(conversationId, AiMessageRole.TOOL,
        truncate(toJson(entry), MAX_STORED_TOOL_CHARS)));
  }

  private AiDto.Proposal toProposalDto(AiActionProposal proposal) {
    AiTool tool;
    String description;
    try {
      tool = registry.requireTool(proposal.getToolName());
      description = tool.describeProposal(fromJson(proposal.getArgumentsJson()));
    } catch (RuntimeException e) {
      description = "Run " + proposal.getToolName() + ".";
    }
    return new AiDto.Proposal(proposal.getId(), proposal.getConversationId(),
        proposal.getGroupId(), proposal.getToolName(), description,
        fromJson(proposal.getArgumentsJson()), proposal.getStatus().name(),
        proposal.getResultSummary(), proposal.getCreatedAt(), proposal.getExpiresAt(),
        proposal.getDecidedAt());
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize AI payload", e);
    }
  }

  private Map<String, Object> fromJson(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
      });
    } catch (Exception e) {
      throw new IllegalStateException("Stored proposal arguments are corrupted");
    }
  }

  private String safeText(String text) {
    return text == null ? "" : text.trim();
  }

  private String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}
