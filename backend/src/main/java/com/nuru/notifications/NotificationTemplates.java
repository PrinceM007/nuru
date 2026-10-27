package com.nuru.notifications;

import com.nuru.domain.NotificationKind;
import java.util.Map;

/**
 * Subject/body templates with {placeholder} substitution. Bodies contain only the
 * recipient's own data or group information they are already allowed to see.
 */
public final class NotificationTemplates {

  private NotificationTemplates() {
  }

  public record Rendered(String subject, String body) {
  }

  public static Rendered render(NotificationKind kind, Map<String, String> vars) {
    return switch (kind) {
      case MEMBERSHIP_DECIDED -> new Rendered(
          "Your request to join " + var(vars, "group") + ": " + var(vars, "decision"),
          "Hello " + var(vars, "name") + ",\n\nYour request to join "
              + var(vars, "group") + " was " + var(vars, "decision") + ". "
              + var(vars, "detail") + "\n\n— Nuru");
      case JOIN_REQUEST_ADMIN_ALERT -> new Rendered(
          "New join request for " + var(vars, "group"),
          var(vars, "applicant") + " requested to join " + var(vars, "group")
              + ". Review it in Nuru.\n\n— Nuru");
      case MEETING_INVITATION -> new Rendered(
          "Meeting invitation: " + var(vars, "title"),
          "Hello " + var(vars, "name") + ",\n\nYou are invited to '" + var(vars, "title")
              + "' for " + var(vars, "group") + " at " + var(vars, "startsAt") + " ("
              + var(vars, "timezone") + "). " + var(vars, "where") + "\n\n— Nuru");
      case MEETING_UPDATED -> new Rendered(
          "Meeting updated: " + var(vars, "title"),
          "Hello " + var(vars, "name") + ",\n\nThe meeting '" + var(vars, "title")
              + "' was updated. New time: " + var(vars, "startsAt") + " ("
              + var(vars, "timezone") + ").\n\n— Nuru");
      case VOTE_OPENED -> new Rendered(
          "Vote opened: " + var(vars, "question"),
          "Hello " + var(vars, "name") + ",\n\nA " + var(vars, "policy")
              + " vote is now open in " + var(vars, "group") + ": '" + var(vars, "question")
              + "'. It closes at " + var(vars, "closesAt") + ".\n\n— Nuru");
      case VOTE_CLOSED -> new Rendered(
          "Vote closed: " + var(vars, "question"),
          "Hello " + var(vars, "name") + ",\n\nThe vote '" + var(vars, "question") + "' in "
              + var(vars, "group") + " is now closed. Results will be published by the "
              + "group admin.\n\n— Nuru");
      case LOAN_DECIDED -> new Rendered(
          "Loan request " + var(vars, "decision") + " in " + var(vars, "group"),
          "Hello " + var(vars, "name") + ",\n\nYour loan request for "
              + var(vars, "amount") + " " + var(vars, "currency") + " was "
              + var(vars, "decision") + ". " + var(vars, "detail") + "\n\n— Nuru");
      case REPAYMENT_CONFIRMED -> new Rendered(
          "Repayment confirmed: " + var(vars, "amount") + " " + var(vars, "currency"),
          "Hello " + var(vars, "name") + ",\n\nYour repayment of " + var(vars, "amount")
              + " " + var(vars, "currency") + " was confirmed. Outstanding balance: "
              + var(vars, "outstanding") + ".\n\n— Nuru");
      case CONTRIBUTION_CONFIRMED -> new Rendered(
          "Contribution confirmed: " + var(vars, "amount") + " " + var(vars, "currency"),
          "Hello " + var(vars, "name") + ",\n\nYour contribution of " + var(vars, "amount")
              + " " + var(vars, "currency") + " for " + var(vars, "period") + " was "
              + "confirmed.\n\n— Nuru");
      case CONTRIBUTION_REMINDER -> new Rendered(
          "Reminder: contribution due for " + var(vars, "group"),
          "Hello " + var(vars, "name") + ",\n\nThis is a reminder that your contribution "
              + "of " + var(vars, "amount") + " " + var(vars, "currency") + " for "
              + var(vars, "period") + " is still outstanding.\n\n— Nuru");
    };
  }

  private static String var(Map<String, String> vars, String key) {
    return vars.getOrDefault(key, "");
  }
}
