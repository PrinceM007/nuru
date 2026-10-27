package com.nuru.groups;

import com.nuru.domain.MembershipStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "memberships")
public class Membership {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MembershipStatus status;

  @Column(name = "joined_at", nullable = false)
  private OffsetDateTime joinedAt;

  protected Membership() {
  }

  public Membership(UUID groupId, UUID userId, UUID roleId, MembershipStatus status) {
    this.groupId = groupId;
    this.userId = userId;
    this.roleId = roleId;
    this.status = status;
  }

  @PrePersist
  void onCreate() {
    joinedAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public void setRoleId(UUID roleId) {
    this.roleId = roleId;
  }

  public MembershipStatus getStatus() {
    return status;
  }

  public void setStatus(MembershipStatus status) {
    this.status = status;
  }

  public OffsetDateTime getJoinedAt() {
    return joinedAt;
  }
}
