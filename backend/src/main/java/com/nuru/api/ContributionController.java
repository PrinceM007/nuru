package com.nuru.api;

import com.nuru.api.dto.FinanceDto;
import com.nuru.api.error.UnprocessableException;
import com.nuru.auth.JwtAuthenticationFilter;
import com.nuru.domain.ScheduleFrequency;
import com.nuru.service.ContributionService;
import com.nuru.service.LedgerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}")
@Tag(name = "Contributions")
public class ContributionController {

  private final ContributionService contributionService;
  private final LedgerService ledgerService;

  public ContributionController(ContributionService contributionService,
      LedgerService ledgerService) {
    this.contributionService = contributionService;
    this.ledgerService = ledgerService;
  }

  @PostMapping("/contribution-schedules")
  public ResponseEntity<FinanceDto.Schedule> createSchedule(@PathVariable UUID groupId,
      @Valid @RequestBody FinanceDto.CreateScheduleRequest request) {
    ScheduleFrequency frequency;
    try {
      frequency = ScheduleFrequency.valueOf(request.frequency().trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException("Unknown schedule frequency: " + request.frequency());
    }
    FinanceDto.Schedule schedule = contributionService.createSchedule(groupId, currentUser(),
        request.title(), request.amount(), frequency, request.dueDay(), request.startDate(),
        request.endDate());
    return ResponseEntity.status(HttpStatus.CREATED).body(schedule);
  }

  @GetMapping("/contribution-schedules")
  public Page<FinanceDto.Schedule> listSchedules(@PathVariable UUID groupId,
      @PageableDefault(size = 20) Pageable pageable) {
    return contributionService.listSchedules(groupId, currentUser(), pageable);
  }

  @PostMapping("/contributions")
  public ResponseEntity<FinanceDto.Contribution> record(@PathVariable UUID groupId,
      @Valid @RequestBody FinanceDto.RecordContributionRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    ContributionService.Created<FinanceDto.Contribution> result =
        contributionService.recordContribution(groupId, currentUser(), request.memberUserId(),
            request.scheduleId(), request.periodStart(), request.periodEnd(), request.amount(),
            request.assessment(), request.notes(), idempotencyKey);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(result.value());
  }

  @GetMapping("/contributions")
  public Page<FinanceDto.Contribution> list(@PathVariable UUID groupId,
      @RequestParam(required = false) UUID memberUserId,
      @RequestParam(required = false) String status,
      @PageableDefault(size = 20) Pageable pageable) {
    return contributionService.listContributions(groupId, currentUser(), memberUserId, status,
        pageable);
  }

  @GetMapping("/contributions/{contributionId}")
  public FinanceDto.Contribution get(@PathVariable UUID groupId,
      @PathVariable UUID contributionId) {
    return contributionService.getContribution(groupId, contributionId, currentUser());
  }

  @PatchMapping("/contributions/{contributionId}")
  public FinanceDto.Contribution updateUnconfirmed(@PathVariable UUID groupId,
      @PathVariable UUID contributionId,
      @Valid @RequestBody FinanceDto.UpdateContributionRequest request) {
    return contributionService.updateUnconfirmed(groupId, contributionId, currentUser(),
        request.amount(), request.periodStart(), request.periodEnd(), request.notes());
  }

  @PostMapping("/contributions/{contributionId}/confirm")
  public FinanceDto.Contribution confirm(@PathVariable UUID groupId,
      @PathVariable UUID contributionId,
      @Valid @RequestBody(required = false) FinanceDto.ConfirmContributionRequest request) {
    return contributionService.confirm(groupId, contributionId, currentUser(),
        request == null ? null : request.occurredAt());
  }

  @PostMapping("/contributions/{contributionId}/reject")
  public FinanceDto.Contribution reject(@PathVariable UUID groupId,
      @PathVariable UUID contributionId,
      @Valid @RequestBody(required = false) FinanceDto.RejectRequest request) {
    return contributionService.reject(groupId, contributionId, currentUser(),
        request == null ? null : request.reason());
  }

  @PostMapping("/contributions/{contributionId}/cancel")
  public FinanceDto.Contribution cancel(@PathVariable UUID groupId,
      @PathVariable UUID contributionId) {
    return contributionService.cancel(groupId, contributionId, currentUser());
  }

  @PostMapping("/contributions/{contributionId}/adjust")
  public ResponseEntity<FinanceDto.Contribution> adjust(@PathVariable UUID groupId,
      @PathVariable UUID contributionId,
      @Valid @RequestBody FinanceDto.AdjustContributionRequest request) {
    FinanceDto.Contribution adjustment = contributionService.adjust(groupId, contributionId,
        currentUser(), request.amount(), request.reason());
    return ResponseEntity.status(HttpStatus.CREATED).body(adjustment);
  }

  @GetMapping("/ledger/summary")
  public FinanceDto.LedgerSummary ledgerSummary(@PathVariable UUID groupId) {
    return ledgerService.summary(groupId, currentUser());
  }

  @GetMapping("/ledger/entries")
  public Page<FinanceDto.LedgerEntry> ledgerEntries(@PathVariable UUID groupId,
      @PageableDefault(size = 20) Pageable pageable) {
    return ledgerService.listEntries(groupId, currentUser(), pageable);
  }

  private UUID currentUser() {
    return JwtAuthenticationFilter.principal();
  }
}
