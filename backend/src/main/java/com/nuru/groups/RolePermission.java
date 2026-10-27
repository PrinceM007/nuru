package com.nuru.groups;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "role_permissions")
public class RolePermission {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  @Column(nullable = false, length = 80)
  private String permission;

  protected RolePermission() {
  }

  public RolePermission(UUID roleId, String permission) {
    this.roleId = roleId;
    this.permission = permission;
  }

  public Long getId() {
    return id;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public String getPermission() {
    return permission;
  }
}
