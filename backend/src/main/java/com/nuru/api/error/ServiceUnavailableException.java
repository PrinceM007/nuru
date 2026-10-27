package com.nuru.api.error;

/** An optional provider (AI, identity, payments, notification gateway) is not configured or down. */
public class ServiceUnavailableException extends RuntimeException {
  public ServiceUnavailableException(String message) {
    super(message);
  }
}
