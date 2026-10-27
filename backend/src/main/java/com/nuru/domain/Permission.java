package com.nuru.domain;

/**
 * Controlled permission vocabulary for group roles. Custom roles may only combine
 * these values — there is no wildcard or free-text permission.
 */
public enum Permission {
  GROUP_SETTINGS_MANAGE,
  MEMBERS_MANAGE,
  ROLES_MANAGE,
  MEETINGS_MANAGE,
  VOTES_MANAGE,
  APPROVAL_POLICIES_MANAGE,
  INVITATIONS_MANAGE,
  CONTRIBUTIONS_CONFIRM,
  LEDGER_VIEW_FULL,
  LOANS_APPROVE,
  STATEMENTS_APPROVE,
  AUDIT_VIEW
}
