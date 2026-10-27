package com.nuru.groups;

import com.nuru.domain.MembershipStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

  Optional<Membership> findByGroupIdAndUserId(UUID groupId, UUID userId);

  Page<Membership> findByGroupId(UUID groupId, Pageable pageable);

  List<Membership> findByGroupIdAndStatus(UUID groupId, MembershipStatus status);

  List<Membership> findByGroupIdAndRoleIdAndStatus(UUID groupId, UUID roleId,
      MembershipStatus status);

  Page<Membership> findByUserIdAndStatus(UUID userId, MembershipStatus status, Pageable pageable);

  long countByGroupIdAndRoleIdAndStatus(UUID groupId, UUID roleId, MembershipStatus status);

  @Query("""
      select count(m) from Membership m, GroupRole r
      where m.groupId = :groupId and m.roleId = r.id
        and r.name = 'ADMIN' and m.status = 'ACTIVE'
      """)
  long countActiveAdmins(UUID groupId);
}
