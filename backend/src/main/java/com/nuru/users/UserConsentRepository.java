package com.nuru.users;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

  Optional<UserConsent> findFirstByUserIdAndPurposeOrderByRecordedAtDesc(UUID userId, String purpose);

  List<UserConsent> findByUserIdOrderByRecordedAtDesc(UUID userId);
}
