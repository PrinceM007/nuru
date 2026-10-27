package com.nuru.finance;

import com.nuru.domain.ContributionStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContributionRepository extends JpaRepository<Contribution, UUID> {

  Optional<Contribution> findByGroupIdAndIdempotencyKey(UUID groupId, String idempotencyKey);

  Page<Contribution> findByGroupId(UUID groupId, Pageable pageable);

  Page<Contribution> findByGroupIdAndMemberUserId(UUID groupId, UUID memberUserId,
      Pageable pageable);

  Page<Contribution> findByGroupIdAndMemberUserIdAndStatus(UUID groupId, UUID memberUserId,
      ContributionStatus status, Pageable pageable);

  Page<Contribution> findByGroupIdAndStatus(UUID groupId, ContributionStatus status,
      Pageable pageable);

  List<Contribution> findByCorrectionOf(UUID correctionOf);

  @Query("""
      select c from Contribution c
      where c.groupId = :groupId and c.memberUserId = :memberUserId
        and c.periodStart <= :periodEnd and c.periodEnd >= :periodStart
      order by c.periodStart, c.createdAt
      """)
  List<Contribution> findOverlapping(UUID groupId, UUID memberUserId, java.time.LocalDate periodStart,
      java.time.LocalDate periodEnd);

  @Query("""
      select coalesce(sum(c.amount), 0) from Contribution c
      where c.groupId = :groupId and c.status in :statuses
      """)
  BigDecimal sumByGroupAndStatuses(UUID groupId, List<ContributionStatus> statuses);

  @Query("""
      select coalesce(sum(c.amount), 0) from Contribution c
      where c.groupId = :groupId and c.memberUserId = :memberUserId
        and c.status in :statuses
      """)
  BigDecimal sumByGroupMemberAndStatuses(UUID groupId, UUID memberUserId,
      List<ContributionStatus> statuses);

  Page<Contribution> findByStatusAndPeriodEndBefore(ContributionStatus status,
      java.time.LocalDate date, Pageable pageable);
}
