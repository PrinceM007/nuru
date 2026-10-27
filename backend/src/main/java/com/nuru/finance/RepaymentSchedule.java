package com.nuru.finance;

import com.nuru.domain.InstallmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "repayment_schedules")
public class RepaymentSchedule {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "loan_id", nullable = false)
  private UUID loanId;

  @Column(name = "installment_no", nullable = false)
  private int installmentNo;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(name = "amount_due", nullable = false, precision = 19, scale = 4)
  private BigDecimal amountDue;

  @Column(name = "amount_paid", nullable = false, precision = 19, scale = 4)
  private BigDecimal amountPaid = BigDecimal.ZERO;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InstallmentStatus status = InstallmentStatus.PENDING;

  protected RepaymentSchedule() {
  }

  public RepaymentSchedule(UUID loanId, int installmentNo, LocalDate dueDate,
      BigDecimal amountDue) {
    this.loanId = loanId;
    this.installmentNo = installmentNo;
    this.dueDate = dueDate;
    this.amountDue = amountDue;
  }

  public BigDecimal remaining() {
    return amountDue.subtract(amountPaid);
  }

  public UUID getId() {
    return id;
  }

  public UUID getLoanId() {
    return loanId;
  }

  public int getInstallmentNo() {
    return installmentNo;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public BigDecimal getAmountDue() {
    return amountDue;
  }

  public BigDecimal getAmountPaid() {
    return amountPaid;
  }

  public void setAmountPaid(BigDecimal amountPaid) {
    this.amountPaid = amountPaid;
  }

  public InstallmentStatus getStatus() {
    return status;
  }

  public void setStatus(InstallmentStatus status) {
    this.status = status;
  }
}
