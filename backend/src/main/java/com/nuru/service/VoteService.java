package com.nuru.service;

import com.nuru.api.dto.VoteDto;
import com.nuru.api.error.ConflictException;
import com.nuru.api.error.ForbiddenOperationException;
import com.nuru.api.error.ResourceNotFoundException;
import com.nuru.api.error.UnprocessableException;
import com.nuru.audit.AuditActions;
import com.nuru.audit.AuditService;
import com.nuru.domain.BallotPolicy;
import com.nuru.domain.MembershipStatus;
import com.nuru.domain.Permission;
import com.nuru.domain.VoteStatus;
import com.nuru.groups.MembershipRepository;
import com.nuru.users.User;
import com.nuru.users.UserRepository;
import com.nuru.voting.Ballot;
import com.nuru.voting.BallotRepository;
import com.nuru.voting.Vote;
import com.nuru.voting.VoteEligibility;
import com.nuru.voting.VoteEligibilityRepository;
import com.nuru.voting.VoteMarker;
import com.nuru.voting.VoteMarkerRepository;
import com.nuru.voting.VoteOption;
import com.nuru.voting.VoteOptionRepository;
import com.nuru.voting.VoteRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Voting with reproducible tallies and genuine secret ballots: for SECRET votes the
 * choice rows carry no voter reference at all, so no API response — and no read-only
 * database access — can link a choice to a voter. One-vote enforcement uses the
 * separate marker table plus a unique constraint.
 *
 * <p>Honest limits (also documented in the API contract): this protects ballot
 * secrecy against application-level and read-only data access. It does not defend
 * against a compromised database server, coerced voters, or timing correlation —
 * no claim of being "unriggable" is made anywhere.
 */
@Service
public class VoteService {

  public static final String ELIGIBILITY_POLICY =
      "Eligibility freezes when the vote opens: everyone who is an active member at that "
          + "moment may cast exactly one ballot while the vote is open. Ballots already cast "
          + "remain valid if the voter later leaves; members who join after opening cannot vote.";

  private final VoteRepository votes;
  private final VoteOptionRepository options;
  private final VoteEligibilityRepository eligibility;
  private final BallotRepository ballots;
  private final VoteMarkerRepository markers;
  private final MembershipRepository memberships;
  private final UserRepository users;
  private final GroupAuth groupAuth;
  private final AuditService auditService;
  private final NotificationPublisher notifications;

  public VoteService(VoteRepository votes, VoteOptionRepository options,
      VoteEligibilityRepository eligibility, BallotRepository ballots,
      VoteMarkerRepository markers, MembershipRepository memberships, UserRepository users,
      GroupAuth groupAuth, AuditService auditService, NotificationPublisher notifications) {
    this.votes = votes;
    this.options = options;
    this.eligibility = eligibility;
    this.ballots = ballots;
    this.markers = markers;
    this.memberships = memberships;
    this.users = users;
    this.groupAuth = groupAuth;
    this.auditService = auditService;
    this.notifications = notifications;
  }

  @Transactional
  public VoteDto.Response create(UUID groupId, UUID callerId, String question,
      String description, BallotPolicy ballotPolicy, OffsetDateTime opensAt,
      OffsetDateTime closesAt, Integer quorum, List<String> optionLabels) {
    groupAuth.requirePermission(groupId, callerId, Permission.VOTES_MANAGE);
    groupAuth.groupOrNotFound(groupId);
    validateWindow(opensAt, closesAt);
    if (quorum != null && quorum < 1) {
      throw new UnprocessableException("Quorum must be at least 1");
    }
    List<String> labels = validateOptions(optionLabels);
    Vote vote = votes.save(new Vote(groupId, question.trim(), blankToNull(description),
        ballotPolicy, opensAt, closesAt, quorum, callerId));
    saveOptions(vote.getId(), labels);
    auditService.record(callerId, groupId, AuditActions.VOTE_CREATED, "Vote",
        vote.getId().toString(), "SUCCESS", "policy=" + ballotPolicy);
    return toDto(vote, callerId);
  }

  @Transactional
  public VoteDto.Response updateDraft(UUID groupId, UUID voteId, UUID callerId, String question,
      String description, OffsetDateTime opensAt, OffsetDateTime closesAt, Integer quorum,
      List<String> optionLabels) {
    groupAuth.requirePermission(groupId, callerId, Permission.VOTES_MANAGE);
    Vote vote = voteInGroup(groupId, voteId);
    if (vote.getStatus() != VoteStatus.DRAFT) {
      throw new UnprocessableException("Only draft votes can be edited");
    }
    if (question != null) {
      vote.setQuestion(question.trim());
    }
    if (description != null) {
      vote.setDescription(blankToNull(description));
    }
    if (opensAt != null) {
      vote.setOpensAt(opensAt);
    }
    if (closesAt != null) {
      vote.setClosesAt(closesAt);
    }
    validateWindow(vote.getOpensAt(), vote.getClosesAt());
    if (quorum != null) {
      if (quorum < 1) {
        throw new UnprocessableException("Quorum must be at least 1");
      }
      vote.setQuorum(quorum);
    }
    if (optionLabels != null) {
      options.deleteByVoteId(vote.getId());
      saveOptions(vote.getId(), validateOptions(optionLabels));
    }
    return toDto(vote, callerId);
  }

  @Transactional
  public void deleteDraft(UUID groupId, UUID voteId, UUID callerId) {
    groupAuth.requirePermission(groupId, callerId, Permission.VOTES_MANAGE);
    Vote vote = voteInGroup(groupId, voteId);
    if (vote.getStatus() != VoteStatus.DRAFT) {
      throw new UnprocessableException("Only draft votes can be deleted");
    }
    options.deleteByVoteId(vote.getId());
    votes.delete(vote);
  }

  @Transactional(readOnly = true)
  public Page<VoteDto.Response> list(UUID groupId, UUID callerId, Pageable pageable) {
    groupAuth.requireMembership(groupId, callerId);
    return votes.findByGroupIdOrderByCreatedAtDesc(groupId, pageable)
        .map(v -> toDto(v, callerId));
  }

  @Transactional(readOnly = true)
  public VoteDto.Response get(UUID groupId, UUID voteId, UUID callerId) {
    groupAuth.requireMembership(groupId, callerId);
    return toDto(voteInGroup(groupId, voteId), callerId);
  }

  @Transactional
  public VoteDto.Response open(UUID groupId, UUID voteId, UUID callerId) {
    groupAuth.requirePermission(groupId, callerId, Permission.VOTES_MANAGE);
    Vote vote = voteInGroup(groupId, voteId);
    if (vote.getStatus() != VoteStatus.DRAFT) {
      throw new UnprocessableException("Only draft votes can be opened");
    }
    // Freeze eligibility: every currently active member.
    List<UUID> eligible = memberships.findByGroupIdAndStatus(groupId, MembershipStatus.ACTIVE)
        .stream().map(m -> m.getUserId()).toList();
    if (eligible.isEmpty()) {
      throw new UnprocessableException("Cannot open a vote with no eligible members");
    }
    for (UUID userId : eligible) {
      eligibility.save(new VoteEligibility(voteId, userId));
    }
    vote.setStatus(VoteStatus.OPEN);
    auditService.record(callerId, groupId, AuditActions.VOTE_OPENED, "Vote",
        vote.getId().toString(), "SUCCESS", "eligible=" + eligible.size());
    notifications.voteOpened(groupId, eligible, vote.getQuestion(),
        vote.getBallotPolicy().name(), vote.getClosesAt().toString());
    return toDto(vote, callerId);
  }

  @Transactional
  public VoteDto.Response close(UUID groupId, UUID voteId, UUID callerId) {
    groupAuth.requirePermission(groupId, callerId, Permission.VOTES_MANAGE);
    Vote vote = voteInGroup(groupId, voteId);
    if (vote.getStatus() != VoteStatus.OPEN) {
      throw new UnprocessableException("Only open votes can be closed");
    }
    vote.setStatus(VoteStatus.CLOSED);
    auditService.record(callerId, groupId, AuditActions.VOTE_CLOSED, "Vote",
        vote.getId().toString(), "SUCCESS",
        "ballots=" + ballots.countByVoteId(voteId));
    notifications.voteClosed(groupId,
        eligibility.findByVoteId(voteId).stream().map(VoteEligibility::getUserId).toList(),
        vote.getQuestion());
    return toDto(vote, callerId);
  }

  @Transactional
  public VoteDto.Response publish(UUID groupId, UUID voteId, UUID callerId) {
    groupAuth.requirePermission(groupId, callerId, Permission.VOTES_MANAGE);
    Vote vote = voteInGroup(groupId, voteId);
    if (vote.getStatus() != VoteStatus.CLOSED) {
      throw new UnprocessableException("Only closed votes can be published");
    }
    vote.setStatus(VoteStatus.PUBLISHED);
    vote.setPublishedAt(OffsetDateTime.now());
    vote.setPublishedBy(callerId);
    auditService.record(callerId, groupId, AuditActions.VOTE_PUBLISHED, "Vote",
        vote.getId().toString(), "SUCCESS", null);
    return toDto(vote, callerId);
  }

  @Transactional
  public VoteDto.MyBallot castBallot(UUID groupId, UUID voteId, UUID callerId, UUID optionId) {
    groupAuth.requireMembership(groupId, callerId);
    Vote vote = voteInGroup(groupId, voteId);
    if (vote.getStatus() != VoteStatus.OPEN) {
      throw new UnprocessableException("Ballots are only accepted while the vote is open");
    }
    OffsetDateTime now = OffsetDateTime.now();
    if (now.isBefore(vote.getOpensAt()) || now.isAfter(vote.getClosesAt())) {
      throw new UnprocessableException("The voting window is not currently open");
    }
    if (!eligibility.existsByVoteIdAndUserId(voteId, callerId)) {
      throw new ForbiddenOperationException("You are not eligible for this vote");
    }
    VoteOption option = options.findById(optionId)
        .orElseThrow(() -> new ResourceNotFoundException("Vote option not found"));
    if (!option.getVoteId().equals(voteId)) {
      throw new ResourceNotFoundException("Vote option not found for this vote");
    }
    if (markers.existsByVoteIdAndVoterUserId(voteId, callerId)) {
      throw new ConflictException("You have already voted");
    }
    try {
      markers.saveAndFlush(new VoteMarker(voteId, callerId));
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("You have already voted");
    }
    UUID storedVoter = vote.getBallotPolicy() == BallotPolicy.ATTRIBUTABLE ? callerId : null;
    ballots.save(new Ballot(voteId, optionId, storedVoter));
    auditService.record(callerId, groupId, AuditActions.BALLOT_CAST, "Vote",
        vote.getId().toString(), "SUCCESS", "policy=" + vote.getBallotPolicy());
    return myBallot(groupId, voteId, callerId);
  }

  @Transactional(readOnly = true)
  public VoteDto.MyBallot myBallot(UUID groupId, UUID voteId, UUID callerId) {
    groupAuth.requireMembership(groupId, callerId);
    Vote vote = voteInGroup(groupId, voteId);
    boolean voted = markers.existsByVoteIdAndVoterUserId(voteId, callerId);
    UUID choice = null;
    if (voted && vote.getBallotPolicy() == BallotPolicy.ATTRIBUTABLE) {
      choice = ballots.findByVoteId(voteId).stream()
          .filter(b -> callerId.equals(b.getVoterUserId()))
          .map(Ballot::getOptionId).findFirst().orElse(null);
    }
    return new VoteDto.MyBallot(voted, choice);
  }

  @Transactional(readOnly = true)
  public VoteDto.Results results(UUID groupId, UUID voteId, UUID callerId) {
    groupAuth.requireMembership(groupId, callerId);
    Vote vote = voteInGroup(groupId, voteId);
    if (vote.getStatus() != VoteStatus.PUBLISHED) {
      throw new UnprocessableException("Results are available after publication");
    }
    List<VoteOption> voteOptions = options.findByVoteIdOrderByPosition(voteId);
    // Recomputed from stored rows on every read: reproducible by construction.
    List<VoteDto.OptionTally> tallies = new ArrayList<>();
    for (VoteOption option : voteOptions) {
      tallies.add(new VoteDto.OptionTally(option.getId(), option.getLabel(),
          ballots.countByVoteIdAndOptionId(voteId, option.getId())));
    }
    long totalBallots = ballots.countByVoteId(voteId);
    long eligibleCount = eligibility.countByVoteId(voteId);
    boolean quorumMet = vote.getQuorum() == null || totalBallots >= vote.getQuorum();
    Map<String, List<VoteDto.VoterChoice>> attributable = null;
    if (vote.getBallotPolicy() == BallotPolicy.ATTRIBUTABLE) {
      List<Ballot> cast = ballots.findByVoteId(voteId);
      Map<UUID, String> names = users.findAllById(
          cast.stream().map(Ballot::getVoterUserId).collect(Collectors.toSet())).stream()
          .collect(Collectors.toMap(User::getId, User::getDisplayName));
      attributable = new LinkedHashMap<>();
      for (VoteOption option : voteOptions) {
        List<VoteDto.VoterChoice> choices = cast.stream()
            .filter(b -> b.getOptionId().equals(option.getId()))
            .map(b -> new VoteDto.VoterChoice(b.getVoterUserId(),
                names.getOrDefault(b.getVoterUserId(), "Unknown member")))
            .toList();
        attributable.put(option.getId().toString(), choices);
      }
    }
    return new VoteDto.Results(voteId, vote.getBallotPolicy().name(), tallies, totalBallots,
        eligibleCount, vote.getQuorum(), quorumMet, vote.getPublishedAt(), attributable);
  }

  private Vote voteInGroup(UUID groupId, UUID voteId) {
    Vote vote = votes.findById(voteId)
        .orElseThrow(() -> new ResourceNotFoundException("Vote not found"));
    if (!vote.getGroupId().equals(groupId)) {
      throw new ResourceNotFoundException("Vote not found in this group");
    }
    return vote;
  }

  private void validateWindow(OffsetDateTime opensAt, OffsetDateTime closesAt) {
    if (opensAt == null || closesAt == null) {
      throw new UnprocessableException("Opening and closing times are required");
    }
    if (!closesAt.isAfter(opensAt)) {
      throw new UnprocessableException("Closing time must be after opening time");
    }
  }

  private List<String> validateOptions(List<String> labels) {
    if (labels == null || labels.size() < 2 || labels.size() > 10) {
      throw new UnprocessableException("Between 2 and 10 options are required");
    }
    List<String> cleaned = labels.stream()
        .map(l -> l == null ? "" : l.trim())
        .filter(l -> !l.isEmpty()).toList();
    if (cleaned.size() != labels.size()) {
      throw new UnprocessableException("Option labels must not be blank");
    }
    if (cleaned.stream().map(String::toLowerCase).distinct().count() != cleaned.size()) {
      throw new UnprocessableException("Option labels must be distinct");
    }
    return cleaned;
  }

  private void saveOptions(UUID voteId, List<String> labels) {
    for (int i = 0; i < labels.size(); i++) {
      options.save(new VoteOption(voteId, labels.get(i), i));
    }
  }

  private String blankToNull(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }

  private VoteDto.Response toDto(Vote vote, UUID callerId) {
    List<VoteDto.Option> optionDtos = options.findByVoteIdOrderByPosition(vote.getId())
        .stream().map(o -> new VoteDto.Option(o.getId(), o.getLabel(), o.getPosition()))
        .toList();
    long eligibleCount = vote.getStatus() == VoteStatus.DRAFT ? memberships
        .findByGroupIdAndStatus(vote.getGroupId(), MembershipStatus.ACTIVE).size()
        : eligibility.countByVoteId(vote.getId());
    boolean voted = markers.existsByVoteIdAndVoterUserId(vote.getId(), callerId);
    return new VoteDto.Response(vote.getId(), vote.getGroupId(), vote.getQuestion(),
        vote.getDescription(), vote.getBallotPolicy().name(), vote.getStatus().name(),
        vote.getOpensAt(), vote.getClosesAt(), vote.getQuorum(), optionDtos, eligibleCount,
        ELIGIBILITY_POLICY, voted, vote.getPublishedAt());
  }
}
