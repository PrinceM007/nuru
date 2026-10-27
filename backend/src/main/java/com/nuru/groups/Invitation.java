package com.nuru.groups;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Invitation link/code. Only a SHA-256 hash of the code is stored; the plaintext
 * code is returned exactly once at creation and must be delivered out of band.
 */
@Entity
@Table(name = "invitations")
public class Invitation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(name = "code_hash", nullable = false, length = 64)
  private String codeHash;

  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  @Column(name = "max_uses", nullable = false)
  private int maxUses;

  @Column(name = "use_count", nullable = false)
  private int useCount;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected Invitation() {
  }

  public Invitation(UUID groupId, String codeHash, UUID roleId, UUID createdBy,
      OffsetDateTime expiresAt, int maxUses) {
    this.groupId = groupId;
    this.codeHash = codeHash;
    this.roleId = roleId;
    this.createdBy = createdBy;
    this.expiresAt = expiresAt;
    this.maxUses = maxUses;
  }

  @PrePersist
  void onCreate() {
    createdAt = OffsetDateTime.now();
  }

  public boolean isUsable() {
    return revokedAt == null
        && expiresAt.isAfter(OffsetDateTime.now())
        && useCount < maxUses;
  }

  public UUID getId() {
    return id;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public String getCodeHash() {
    return codeHash;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public OffsetDateTime getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(OffsetDateTime revokedAt) {
    this.revokedAt = revokedAt;
  }

  public int getMaxUses() {
    return maxUses;
  }

  public int getUseCount() {
    return useCount;
  }

  public void setUseCount(int useCount) {
    this.useCount = useCount;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
