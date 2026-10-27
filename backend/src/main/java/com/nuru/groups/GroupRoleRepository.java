package com.nuru.groups;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRoleRepository extends JpaRepository<GroupRole, UUID> {

  List<GroupRole> findByGroupId(UUID groupId);

  Optional<GroupRole> findByGroupIdAndName(UUID groupId, String name);

  boolean existsByGroupIdAndName(UUID groupId, String name);
}
