package com.nuru.ai;

import com.nuru.ai.providers.DeterministicDevAiProvider;
import com.nuru.ai.providers.OpenAiCompatibleProvider;
import com.nuru.ai.providers.UnconfiguredAiProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Selects the active AI provider from runtime configuration. */
@Configuration
public class AiProviderConfig {

  @Bean
  @Primary
  public AiProvider activeAiProvider(UnconfiguredAiProvider unconfigured,
      DeterministicDevAiProvider dev, OpenAiCompatibleProvider openAiCompatible,
      @Value("${nuru.ai.provider:}") String configured) {
    if ("DEV".equalsIgnoreCase(configured)) {
      return dev;
    }
    if ("OPENAI_COMPATIBLE".equalsIgnoreCase(configured)) {
      return openAiCompatible;
    }
    return unconfigured;
  }
}
