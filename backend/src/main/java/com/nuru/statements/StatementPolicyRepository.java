package com.nuru.statements;

import com.nuru.domain.StatementRequestType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatementPolicyRepository extends JpaRepository<StatementPolicy, Long> {

  Optional<StatementPolicy> findByGroupIdAndRequestType(UUID groupId,
      StatementRequestType requestType);

  List<StatementPolicy> findByGroupId(UUID groupId);
}
