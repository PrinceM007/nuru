package com.nuru.groups;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

  List<RolePermission> findByRoleId(UUID roleId);

  void deleteByRoleId(UUID roleId);
}
