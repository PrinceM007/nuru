package com.nuru.api;

import com.nuru.api.dto.VoteDto;
import com.nuru.api.error.UnprocessableException;
import com.nuru.auth.JwtAuthenticationFilter;
import com.nuru.domain.BallotPolicy;
import com.nuru.service.VoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/votes")
@Tag(name = "Votes")
public class VoteController {

  private final VoteService voteService;

  public VoteController(VoteService voteService) {
    this.voteService = voteService;
  }

  @PostMapping
  public ResponseEntity<VoteDto.Response> create(@PathVariable UUID groupId,
      @Valid @RequestBody VoteDto.CreateRequest request) {
    VoteDto.Response vote = voteService.create(groupId, currentUser(), request.question(),
        request.description(), parsePolicy(request.ballotPolicy()), request.opensAt(),
        request.closesAt(), request.quorum(), request.options());
    return ResponseEntity.status(HttpStatus.CREATED).body(vote);
  }

  @GetMapping
  public Page<VoteDto.Response> list(@PathVariable UUID groupId,
      @PageableDefault(size = 20) Pageable pageable) {
    return voteService.list(groupId, currentUser(), pageable);
  }

  @GetMapping("/{voteId}")
  public VoteDto.Response get(@PathVariable UUID groupId, @PathVariable UUID voteId) {
    return voteService.get(groupId, voteId, currentUser());
  }

  @PatchMapping("/{voteId}")
  public VoteDto.Response updateDraft(@PathVariable UUID groupId, @PathVariable UUID voteId,
      @Valid @RequestBody VoteDto.UpdateRequest request) {
    return voteService.updateDraft(groupId, voteId, currentUser(), request.question(),
        request.description(), request.opensAt(), request.closesAt(), request.quorum(),
        request.options());
  }

  @DeleteMapping("/{voteId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteDraft(@PathVariable UUID groupId, @PathVariable UUID voteId) {
    voteService.deleteDraft(groupId, voteId, currentUser());
  }

  @PostMapping("/{voteId}/open")
  public VoteDto.Response open(@PathVariable UUID groupId, @PathVariable UUID voteId) {
    return voteService.open(groupId, voteId, currentUser());
  }

  @PostMapping("/{voteId}/close")
  public VoteDto.Response close(@PathVariable UUID groupId, @PathVariable UUID voteId) {
    return voteService.close(groupId, voteId, currentUser());
  }

  @PostMapping("/{voteId}/publish")
  public VoteDto.Response publish(@PathVariable UUID groupId, @PathVariable UUID voteId) {
    return voteService.publish(groupId, voteId, currentUser());
  }

  @PostMapping("/{voteId}/ballots")
  public ResponseEntity<VoteDto.MyBallot> cast(@PathVariable UUID groupId,
      @PathVariable UUID voteId, @Valid @RequestBody VoteDto.CastBallotRequest request) {
    VoteDto.MyBallot ballot = voteService.castBallot(groupId, voteId, currentUser(),
        request.optionId());
    return ResponseEntity.status(HttpStatus.CREATED).body(ballot);
  }

  @GetMapping("/{voteId}/my-ballot")
  public VoteDto.MyBallot myBallot(@PathVariable UUID groupId, @PathVariable UUID voteId) {
    return voteService.myBallot(groupId, voteId, currentUser());
  }

  @GetMapping("/{voteId}/results")
  public VoteDto.Results results(@PathVariable UUID groupId, @PathVariable UUID voteId) {
    return voteService.results(groupId, voteId, currentUser());
  }

  private BallotPolicy parsePolicy(String policy) {
    try {
      return BallotPolicy.valueOf(policy.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException("Unknown ballot policy: " + policy);
    }
  }

  private UUID currentUser() {
    return JwtAuthenticationFilter.principal();
  }
}
