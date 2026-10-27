package com.nuru.provider.email;

import java.util.UUID;

/** Email gateway abstraction. Bodies are rendered at send time, never stored by Nuru. */
public interface EmailProvider {

  String name();

  boolean isAvailable();

  EmailResult send(UUID recipientUserId, String toEmail, String subject, String body);

  record EmailResult(boolean delivered, String providerReference, String detail) {
  }
}
