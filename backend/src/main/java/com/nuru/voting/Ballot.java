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

/**
 * A cast choice. For SECRET votes {@code voterUserId} is always NULL so that
 * no query — by admins, AI tools, or anyone with read access — can link a
 * choice to a voter. One-vote enforcement lives in {@link VoteMarker}.
 */
@Entity
@Table(name = "ballots")
public class Ballot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "vote_id", nullable = false)
  private UUID voteId;

  @Column(name = "option_id", nullable = false)
  private UUID optionId;

  @Column(name = "voter_user_id")
  private UUID voterUserId;

  @Column(name = "cast_at", nullable = false)
  private OffsetDateTime castAt;

  protected Ballot() {
  }

  public Ballot(UUID voteId, UUID optionId, UUID voterUserId) {
    this.voteId = voteId;
    this.optionId = optionId;
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

  public UUID getOptionId() {
    return optionId;
  }

  public UUID getVoterUserId() {
    return voterUserId;
  }

  public OffsetDateTime getCastAt() {
    return castAt;
  }
}
