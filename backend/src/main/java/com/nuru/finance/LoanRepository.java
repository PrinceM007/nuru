package com.nuru.finance;

import com.nuru.domain.LoanStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

  Optional<Loan> findByGroupIdAndIdempotencyKey(UUID groupId, String idempotencyKey);

  Page<Loan> findByGroupId(UUID groupId, Pageable pageable);

  Page<Loan> findByGroupIdAndBorrowerUserId(UUID groupId, UUID borrowerUserId, Pageable pageable);

  List<Loan> findByGroupIdAndBorrowerUserId(UUID groupId, UUID borrowerUserId);

  @Query("""
      select coalesce(sum(l.totalDue), 0) from Loan l
      where l.groupId = :groupId and l.status in :statuses
      """)
  BigDecimal sumTotalDueByGroupAndStatuses(UUID groupId, List<LoanStatus> statuses);
}
