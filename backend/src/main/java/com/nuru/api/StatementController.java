package com.nuru.api;

import com.nuru.api.dto.StatementDto;
import com.nuru.api.error.UnprocessableException;
import com.nuru.auth.JwtAuthenticationFilter;
import com.nuru.domain.StatementRequestType;
import com.nuru.service.StatementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/statements")
@Tag(name = "Statements")
public class StatementController {

  private final StatementService statementService;

  public StatementController(StatementService statementService) {
    this.statementService = statementService;
  }

  @GetMapping("/policies")
  public List<StatementDto.Policy> policies(@PathVariable UUID groupId) {
    return statementService.listPolicies(groupId, currentUser());
  }

  @PutMapping("/policies")
  public StatementDto.Policy configurePolicy(@PathVariable UUID groupId,
      @Valid @RequestBody StatementDto.PolicyRequest request) {
    return statementService.configurePolicy(groupId, currentUser(),
        parseType(request.requestType()), request.approvalRequired());
  }

  @PostMapping("/requests")
  public ResponseEntity<StatementDto.Request> request(@PathVariable UUID groupId,
      @Valid @RequestBody StatementDto.CreateRequest request) {
    StatementDto.Request created = statementService.request(groupId, currentUser(),
        parseType(request.requestType()), request.periodStart(), request.periodEnd());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/requests")
  public Page<StatementDto.Request> list(@PathVariable UUID groupId,
      @PageableDefault(size = 20) Pageable pageable) {
    return statementService.list(groupId, currentUser(), pageable);
  }

  @PostMapping("/requests/{requestId}/approve")
  public StatementDto.Request approve(@PathVariable UUID groupId,
      @PathVariable UUID requestId) {
    return statementService.approve(groupId, requestId, currentUser());
  }

  @PostMapping("/requests/{requestId}/reject")
  public StatementDto.Request reject(@PathVariable UUID groupId, @PathVariable UUID requestId,
      @Valid @RequestBody(required = false) StatementDto.RejectRequest request) {
    return statementService.reject(groupId, requestId, currentUser(),
        request == null ? null : request.reason());
  }

  @GetMapping("/requests/{requestId}/document")
  public StatementDto.Document document(@PathVariable UUID groupId,
      @PathVariable UUID requestId) {
    return statementService.document(groupId, requestId, currentUser());
  }

  private StatementRequestType parseType(String type) {
    try {
      return StatementRequestType.valueOf(type.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException("Unknown statement request type: " + type);
    }
  }

  private UUID currentUser() {
    return JwtAuthenticationFilter.principal();
  }
}
