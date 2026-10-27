package com.nuru.provider.sms;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Development adapter: logs the recipient (never the body) and reports SIMULATED. */
@Component
public class LoggingSmsProvider implements SmsProvider {

  private static final Logger log = LoggerFactory.getLogger(LoggingSmsProvider.class);

  @Override
  public String name() {
    return "LOG";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public SmsResult send(UUID recipientUserId, String toPhone, String body) {
    log.info("DEV sms to {} (user {})", toPhone, recipientUserId);
    return new SmsResult(false, null, "SIMULATED — dev adapter, nothing was sent");
  }
}
