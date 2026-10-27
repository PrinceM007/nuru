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

/** Records THAT a user voted (for one-vote enforcement and turnout), never HOW. */
@Entity
@Table(name = "vote_markers")
public class VoteMarker {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "vote_id", nullable = false)
  private UUID voteId;

  @Column(name = "voter_user_id", nullable = false)
  private UUID voterUserId;

  @Column(name = "cast_at", nullable = false)
  private OffsetDateTime castAt;

  protected VoteMarker() {
  }

  public VoteMarker(UUID voteId, UUID voterUserId) {
    this.voteId = voteId;
    this.voterUserId = voterUserId;
  }

  @PrePersist
  void onCreate() {
    castAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public UUID getVoteId() {
    return voteId;
  }

  public UUID getVoterUserId() {
    return voterUserId;
  }

  public OffsetDateTime getCastAt() {
    return castAt;
  }
}
