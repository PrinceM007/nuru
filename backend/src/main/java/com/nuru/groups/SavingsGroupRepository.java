package com.nuru.groups;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsGroupRepository extends JpaRepository<SavingsGroup, UUID> {
}
