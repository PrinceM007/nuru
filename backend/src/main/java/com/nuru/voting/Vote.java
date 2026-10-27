package com.nuru.voting;

import com.nuru.domain.BallotPolicy;
import com.nuru.domain.VoteStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "votes")
public class Vote {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(nullable = false, length = 500)
  private String question;

  @Column(length = 2000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "ballot_policy", nullable = false, length = 20)
  private BallotPolicy ballotPolicy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private VoteStatus status;

  @Column(name = "opens_at", nullable = false)
  private OffsetDateTime opensAt;

  @Column(name = "closes_at", nullable = false)
  private OffsetDateTime closesAt;

  private Integer quorum;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  @Column(name = "published_by")
  private UUID publishedBy;

  protected Vote() {
  }

  public Vote(UUID groupId, String question, String description, BallotPolicy ballotPolicy,
      OffsetDateTime opensAt, OffsetDateTime closesAt, Integer quorum, UUID createdBy) {
    this.groupId = groupId;
    this.question = question;
    this.description = description;
    this.ballotPolicy = ballotPolicy;
    this.status = VoteStatus.DRAFT;
    this.opensAt = opensAt;
    this.closesAt = closesAt;
    this.quorum = quorum;
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

  public String getQuestion() {
    return question;
  }

  public void setQuestion(String question) {
    this.question = question;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public BallotPolicy getBallotPolicy() {
    return ballotPolicy;
  }

  public VoteStatus getStatus() {
    return status;
  }

  public void setStatus(VoteStatus status) {
    this.status = status;
  }

  public OffsetDateTime getOpensAt() {
    return opensAt;
  }

  public void setOpensAt(OffsetDateTime opensAt) {
    this.opensAt = opensAt;
  }

  public OffsetDateTime getClosesAt() {
    return closesAt;
  }

  public void setClosesAt(OffsetDateTime closesAt) {
    this.closesAt = closesAt;
  }

  public Integer getQuorum() {
    return quorum;
  }

  public void setQuorum(Integer quorum) {
    this.quorum = quorum;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(OffsetDateTime publishedAt) {
    this.publishedAt = publishedAt;
  }

  public UUID getPublishedBy() {
    return publishedBy;
  }

  public void setPublishedBy(UUID publishedBy) {
    this.publishedBy = publishedBy;
  }
}
