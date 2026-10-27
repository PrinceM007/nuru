package com.nuru.meetings;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

  Page<Meeting> findByGroupIdOrderByStartsAtDesc(UUID groupId, Pageable pageable);
}
