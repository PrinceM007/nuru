package com.nuru.provider.sms;

import java.util.UUID;

/** SMS gateway abstraction. Message bodies are never stored or logged by Nuru. */
public interface SmsProvider {

  String name();

  boolean isAvailable();

  SmsResult send(UUID recipientUserId, String toPhone, String body);

  record SmsResult(boolean delivered, String providerReference, String detail) {
  }
}
