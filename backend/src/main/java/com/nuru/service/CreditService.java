package com.nuru.service;

import com.nuru.api.dto.IdentityDto;
import com.nuru.api.error.ForbiddenOperationException;
import com.nuru.api.error.ServiceUnavailableException;
import com.nuru.domain.Permission;
import com.nuru.provider.credit.CreditScoreProvider;
import com.nuru.provider.credit.GroupHistoryCreditProvider;
import com.nuru.provider.credit.UnconfiguredCreditProvider;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditService {

  private final CreditScoreProvider provider;
  private final GroupAuth groupAuth;

  public CreditService(UnconfiguredCreditProvider unconfigured,
      GroupHistoryCreditProvider groupHistory, GroupAuth groupAuth,
      @Value("${nuru.credit.provider:}") String configured) {
    this.provider = "GROUP_HISTORY".equalsIgnoreCase(configured) ? groupHistory : unconfigured;
    this.groupAuth = groupAuth;
  }

  @Transactional(readOnly = true)
  public IdentityDto.CreditEstimate estimate(UUID groupId, UUID callerId, UUID memberUserId) {
    groupAuth.requireMembership(groupId, callerId);
    if (!provider.isAvailable()) {
      throw new ServiceUnavailableException("Credit information is not configured");
    }
    UUID member = memberUserId == null ? callerId : memberUserId;
    if (!member.equals(callerId)
        && !groupAuth.hasPermission(groupId, callerId, Permission.LEDGER_VIEW_FULL)) {
      throw new ForbiddenOperationException("Cannot view another member's credit estimate");
    }
    CreditScoreProvider.CreditEstimate estimate = provider.estimate(groupId, member);
    return IdentityDto.CreditEstimate.from(provider.name(), member, estimate);
  }
}
