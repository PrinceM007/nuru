package com.nuru.voting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Frozen eligibility snapshot row: user was an active member when the vote opened. */
@Entity
@Table(name = "vote_eligibility")
public class VoteEligibility {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "vote_id", nullable = false)
  private UUID voteId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "snapshot_at", nullable = false)
  private OffsetDateTime snapshotAt;

  protected VoteEligibility() {
  }

  public VoteEligibility(UUID voteId, UUID userId) {
    this.voteId = voteId;
    this.userId = userId;
  }

  @PrePersist
  void onCreate() {
    snapshotAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public UUID getVoteId() {
    return voteId;
  }

  public UUID getUserId() {
    return userId;
  }

  public OffsetDateTime getSnapshotAt() {
    return snapshotAt;
  }
}
