package com.nuru.api;

import com.nuru.api.dto.AuditDto;
import com.nuru.audit.AuditService;
import com.nuru.auth.JwtAuthenticationFilter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/audit")
@Tag(name = "Audit")
public class AuditController {

  private final AuditService auditService;

  public AuditController(AuditService auditService) {
    this.auditService = auditService;
  }

  @GetMapping
  public Page<AuditDto.Event> events(@PathVariable UUID groupId,
      @PageableDefault(size = 50) Pageable pageable) {
    return auditService.eventsForGroup(groupId, JwtAuthenticationFilter.principal(), pageable)
        .map(AuditDto.Event::from);
  }
}
