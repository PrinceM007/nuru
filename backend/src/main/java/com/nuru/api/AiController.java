package com.nuru.api;

import com.nuru.api.dto.AiDto;
import com.nuru.auth.JwtAuthenticationFilter;
import com.nuru.service.AiService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Assistant")
public class AiController {

  private final AiService aiService;

  public AiController(AiService aiService) {
    this.aiService = aiService;
  }

  @GetMapping("/status")
  public AiDto.Status status() {
    JwtAuthenticationFilter.principal();
    return aiService.status();
  }

  @PostMapping("/conversations")
  public ResponseEntity<AiDto.Conversation> createConversation(
      @Valid @RequestBody(required = false) AiDto.CreateConversationRequest request) {
    AiDto.Conversation conversation = aiService.createConversation(currentUser(),
        request == null ? null : request.groupId(),
        request == null ? null : request.title());
    return ResponseEntity.status(HttpStatus.CREATED).body(conversation);
  }

  @GetMapping("/conversations")
  public Page<AiDto.Conversation> listConversations(
      @PageableDefault(size = 20) Pageable pageable) {
    return aiService.listConversations(currentUser(), pageable);
  }

  @GetMapping("/conversations/{conversationId}")
  public AiDto.ConversationDetail getConversation(@PathVariable UUID conversationId) {
    return aiService.getConversation(currentUser(), conversationId);
  }

  @DeleteMapping("/conversations/{conversationId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteConversation(@PathVariable UUID conversationId) {
    aiService.deleteConversation(currentUser(), conversationId);
  }

  @PostMapping("/conversations/{conversationId}/messages")
  public AiDto.ChatResponse sendMessage(@PathVariable UUID conversationId,
      @Valid @RequestBody AiDto.ChatRequest request) {
    return aiService.sendMessage(currentUser(), conversationId, request.text());
  }

  @GetMapping("/proposals")
  public List<AiDto.Proposal> listProposals(@RequestParam(required = false) String status) {
    return aiService.listProposals(currentUser(), status);
  }

  @PostMapping("/proposals/{proposalId}/confirm")
  public AiDto.Proposal confirm(@PathVariable UUID proposalId) {
    return aiService.confirmProposal(currentUser(), proposalId);
  }

  @PostMapping("/proposals/{proposalId}/reject")
  public AiDto.Proposal reject(@PathVariable UUID proposalId) {
    return aiService.rejectProposal(currentUser(), proposalId);
  }

  private UUID currentUser() {
    return JwtAuthenticationFilter.principal();
  }
}
