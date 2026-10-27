package com.nuru.api.error;

/** Syntactically valid request that violates a domain rule or state transition. */
public class UnprocessableException extends RuntimeException {
  public UnprocessableException(String message) {
    super(message);
  }
}
