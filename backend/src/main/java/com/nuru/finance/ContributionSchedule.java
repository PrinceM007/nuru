package com.nuru.finance;

import com.nuru.domain.ScheduleFrequency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "contribution_schedules")
public class ContributionSchedule {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(nullable = false, length = 160)
  private String title;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ScheduleFrequency frequency;

  @Column(name = "due_day")
  private Integer dueDay;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected ContributionSchedule() {
  }

  public ContributionSchedule(UUID groupId, String title, BigDecimal amount, String currency,
      ScheduleFrequency frequency, Integer dueDay, LocalDate startDate, LocalDate endDate,
      UUID createdBy) {
    this.groupId = groupId;
    this.title = title;
    this.amount = amount;
    this.currency = currency;
    this.frequency = frequency;
    this.dueDay = dueDay;
    this.startDate = startDate;
    this.endDate = endDate;
    this.createdBy = createdBy;
  }

  @PrePersist
  void onCreate() {
    createdAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public String getTitle() {
    return title;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public ScheduleFrequency getFrequency() {
    return frequency;
  }

  public Integer getDueDay() {
    return dueDay;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
