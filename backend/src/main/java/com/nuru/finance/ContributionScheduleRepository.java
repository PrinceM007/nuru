package com.nuru.finance;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContributionScheduleRepository extends JpaRepository<ContributionSchedule, UUID> {

  Page<ContributionSchedule> findByGroupId(UUID groupId, Pageable pageable);
}
