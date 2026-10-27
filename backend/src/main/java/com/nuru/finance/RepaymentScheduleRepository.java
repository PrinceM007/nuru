package com.nuru.finance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepaymentScheduleRepository extends JpaRepository<RepaymentSchedule, UUID> {

  List<RepaymentSchedule> findByLoanIdOrderByInstallmentNo(UUID loanId);
}
