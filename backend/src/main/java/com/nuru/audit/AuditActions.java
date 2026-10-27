package com.nuru.audit;

/** Canonical audit action names shared by services and the AI tool layer. */
public final class AuditActions {

  private AuditActions() {
  }

  public static final String USER_REGISTERED = "user.registered";
  public static final String USER_SIGNED_IN = "user.signed_in";
  public static final String USER_SIGNED_OUT = "user.signed_out";
  public static final String CONSENT_RECORDED = "user.consent_recorded";

  public static final String GROUP_CREATED = "group.created";
  public static final String GROUP_UPDATED = "group.updated";
  public static final String MEMBER_JOIN_REQUESTED = "membership.join_requested";
  public static final String MEMBER_APPROVED = "membership.approved";
  public static final String MEMBER_REJECTED = "membership.rejected";
  public static final String MEMBER_REMOVED = "membership.removed";
  public static final String ROLE_CHANGED = "membership.role_changed";
  public static final String ADMIN_TRANSFERRED = "membership.admin_transferred";
  public static final String INVITATION_CREATED = "invitation.created";
  public static final String INVITATION_REVOKED = "invitation.revoked";
  public static final String INVITATION_ACCEPTED = "invitation.accepted";

  public static final String CONTRIBUTION_RECORDED = "contribution.recorded";
  public static final String CONTRIBUTION_CONFIRMED = "contribution.confirmed";
  public static final String CONTRIBUTION_CORRECTED = "contribution.corrected";
  public static final String LOAN_REQUESTED = "loan.requested";
  public static final String LOAN_DECIDED = "loan.decided";
  public static final String LOAN_REPAID = "loan.repaid";

  public static final String MEETING_CREATED = "meeting.created";
  public static final String MEETING_UPDATED = "meeting.updated";
  public static final String MEETING_CANCELLED = "meeting.cancelled";
  public static final String ATTENDANCE_RECORDED = "meeting.attendance_recorded";

  public static final String VOTE_CREATED = "vote.created";
  public static final String VOTE_OPENED = "vote.opened";
  public static final String VOTE_CLOSED = "vote.closed";
  public static final String VOTE_PUBLISHED = "vote.published";
  public static final String BALLOT_CAST = "vote.ballot_cast";

  public static final String STATEMENT_REQUESTED = "statement.requested";
  public static final String STATEMENT_DECIDED = "statement.decided";

  public static final String AI_ACTION_PROPOSED = "ai.action_proposed";
  public static final String AI_ACTION_CONFIRMED = "ai.action_confirmed";
  public static final String AI_ACTION_REJECTED = "ai.action_rejected";

  public static final String VERIFICATION_CHANGED = "verification.status_changed";
  public static final String NOTIFICATION_QUEUED = "notification.queued";
  public static final String NOTIFICATION_DELIVERY_RECORDED = "notification.delivery_recorded";
}
