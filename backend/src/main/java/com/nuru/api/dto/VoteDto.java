package com.nuru.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VoteDto {

  private VoteDto() {
  }

  public record CreateRequest(
      @NotBlank @Size(min = 1, max = 500) String question,
      @Size(max = 2000) String description,
      @NotNull String ballotPolicy,
      @NotNull OffsetDateTime opensAt,
      @NotNull OffsetDateTime closesAt,
      Integer quorum,
      @NotNull @Size(min = 2, max = 10) List<String> options) {
  }

  public record UpdateRequest(
      @Size(min = 1, max = 500) String question,
      @Size(max = 2000) String description,
      OffsetDateTime opensAt,
      OffsetDateTime closesAt,
      Integer quorum,
      @Size(min = 2, max = 10) List<String> options) {
  }

  public record CastBallotRequest(@NotNull UUID optionId) {
  }

  public record Option(UUID id, String label, int position) {
  }

  public record Response(UUID id, UUID groupId, String question, String description,
      String ballotPolicy, String status, OffsetDateTime opensAt, OffsetDateTime closesAt,
      Integer quorum, List<Option> options, long eligibleCount, String eligibilityPolicy,
      boolean voted, OffsetDateTime publishedAt) {
  }

  public record MyBallot(boolean voted, UUID optionId) {
  }

  public record OptionTally(UUID optionId, String label, long votes) {
  }

  public record VoterChoice(UUID userId, String displayName) {
  }

  public record Results(UUID voteId, String ballotPolicy, List<OptionTally> tallies,
      long totalBallots, long eligibleCount, Integer quorum, boolean quorumMet,
      OffsetDateTime publishedAt,
      // Present only for ATTRIBUTABLE votes: option id -> voters. Always null for SECRET.
      Map<String, List<VoterChoice>> attributableChoices) {
  }
}
