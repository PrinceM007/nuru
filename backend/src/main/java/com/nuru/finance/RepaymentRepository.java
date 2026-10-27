package com.nuru.finance;

import com.nuru.domain.LoanStatus;
import com.nuru.domain.RepaymentStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RepaymentRepository extends JpaRepository<Repayment, UUID> {

  Optional<Repayment> findByLoanIdAndIdempotencyKey(UUID loanId, String idempotencyKey);

  Page<Repayment> findByLoanId(UUID loanId, Pageable pageable);

  @Query("""
      select coalesce(sum(r.amount), 0) from Repayment r
      where r.loanId = :loanId and r.status = :status
      """)
  BigDecimal sumByLoanAndStatus(UUID loanId, RepaymentStatus status);

  @Query("""
      select coalesce(sum(r.amount), 0) from Repayment r, Loan l
      where r.loanId = l.id and l.groupId = :groupId and l.status in :statuses
        and r.status = 'CONFIRMED'
      """)
  BigDecimal sumConfirmedByGroupAndLoanStatuses(UUID groupId, List<LoanStatus> statuses);
}
