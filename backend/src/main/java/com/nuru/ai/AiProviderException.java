package com.nuru.ai;

/** Provider-side failure: timeout, refusal, transport error, or bad provider response. */
public class AiProviderException extends Exception {

  public enum Kind {
    TIMEOUT,
    REFUSAL,
    TRANSPORT,
    INVALID_RESPONSE
  }

  private final Kind kind;

  public AiProviderException(Kind kind, String safeMessage) {
    super(safeMessage);
    this.kind = kind;
  }

  public AiProviderException(Kind kind, String safeMessage, Throwable cause) {
    super(safeMessage, cause);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }
}
