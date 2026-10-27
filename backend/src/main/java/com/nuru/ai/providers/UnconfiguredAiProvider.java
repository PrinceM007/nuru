package com.nuru.ai.providers;

import com.nuru.ai.AiProvider;
import com.nuru.ai.AiProviderException;
import org.springframework.stereotype.Component;

/** Default when no AI credentials are configured: plans nothing, says so explicitly. */
@Component
public class UnconfiguredAiProvider implements AiProvider {

  @Override
  public String name() {
    return "UNCONFIGURED";
  }

  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public String statusDetail() {
    return "No AI provider is configured. Set NURU_AI_PROVIDER=DEV for local development "
        + "or configure an OpenAI-compatible provider.";
  }

  @Override
  public AiPlannerResponse plan(AiPlannerRequest request) throws AiProviderException {
    throw new AiProviderException(AiProviderException.Kind.TRANSPORT,
        "AI provider is not configured");
  }
}
