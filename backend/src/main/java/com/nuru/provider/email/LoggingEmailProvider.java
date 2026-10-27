package com.nuru.provider.email;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Development adapter: logs recipient + subject (never the body) and reports
 * SIMULATED. Deliberately never claims delivery.
 */
@Component
public class LoggingEmailProvider implements EmailProvider {

  private static final Logger log = LoggerFactory.getLogger(LoggingEmailProvider.class);

  @Override
  public String name() {
    return "LOG";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public EmailResult send(UUID recipientUserId, String toEmail, String subject, String body) {
    log.info("DEV email to {} (user {}): {}", toEmail, recipientUserId, subject);
    return new EmailResult(false, null, "SIMULATED — dev adapter, nothing was sent");
  }
}
