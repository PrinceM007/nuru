package com.nuru.groups;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "group_roles")
public class GroupRole {

  public static final String ADMIN = "ADMIN";
  public static final String TREASURER = "TREASURER";
  public static final String MEMBER = "MEMBER";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(nullable = false, length = 60)
  private String name;

  @Column(name = "is_system", nullable = false)
  private boolean system;

  protected GroupRole() {
  }

  public GroupRole(UUID groupId, String name, boolean system) {
    this.groupId = groupId;
    this.name = name;
    this.system = system;
  }

  public UUID getId() {
    return id;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public String getName() {
    return name;
  }

  public boolean isSystem() {
    return system;
  }
}
