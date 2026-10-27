package com.nuru.api;

import com.nuru.api.dto.IdentityDto;
import com.nuru.auth.JwtAuthenticationFilter;
import com.nuru.service.CreditService;
import com.nuru.service.VerificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Identity & Credit")
public class IdentityController {

  private final VerificationService verificationService;
  private final CreditService creditService;

  public IdentityController(VerificationService verificationService,
      CreditService creditService) {
    this.verificationService = verificationService;
    this.creditService = creditService;
  }

  @GetMapping("/api/v1/verification/me")
  public IdentityDto.Status myStatus() {
    return verificationService.status(currentUser());
  }

  @PostMapping("/api/v1/verification/me/start")
  public IdentityDto.Status start() {
    return verificationService.start(currentUser());
  }

  @PostMapping("/api/v1/verification/me/refresh")
  public IdentityDto.Status refresh() {
    return verificationService.refresh(currentUser());
  }

  @GetMapping("/api/v1/groups/{groupId}/credit/estimate")
  public IdentityDto.CreditEstimate estimate(@PathVariable UUID groupId,
      @RequestParam(required = false) UUID memberUserId) {
    return creditService.estimate(groupId, currentUser(), memberUserId);
  }

  private UUID currentUser() {
    return JwtAuthenticationFilter.principal();
  }
}
