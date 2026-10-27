package com.nuru.voting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "vote_options")
public class VoteOption {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "vote_id", nullable = false)
  private UUID voteId;

  @Column(nullable = false, length = 200)
  private String label;

  @Column(nullable = false)
  private int position;

  protected VoteOption() {
  }

  public VoteOption(UUID voteId, String label, int position) {
    this.voteId = voteId;
    this.label = label;
    this.position = position;
  }

  public UUID getId() {
    return id;
  }

  public UUID getVoteId() {
    return voteId;
  }

  public String getLabel() {
    return label;
  }

  public int getPosition() {
    return position;
  }
}
