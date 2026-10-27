package com.nuru.meetings;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingNoteRepository extends JpaRepository<MeetingNote, UUID> {

  List<MeetingNote> findByMeetingIdOrderByCreatedAt(UUID meetingId);
}
