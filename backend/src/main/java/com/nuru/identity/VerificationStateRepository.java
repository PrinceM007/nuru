package com.nuru.identity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationStateRepository extends JpaRepository<VerificationState, UUID> {
}
