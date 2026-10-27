package com.nuru.meetings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

  List<MeetingParticipant> findByMeetingId(UUID meetingId);

  Optional<MeetingParticipant> findByMeetingIdAndUserId(UUID meetingId, UUID userId);

  void deleteByMeetingIdAndUserId(UUID meetingId, UUID userId);
}
