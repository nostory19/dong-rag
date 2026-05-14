package com.dong.dongrag.config;

import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatDefaultsConfig {

    @Bean
    public ChatClientCustomizer dongragChatClientCustomizer(DongragAiProperties dongragAiProperties) {
        return builder -> builder.defaultOptions(OpenAiChatOptions.builder()
                .temperature(dongragAiProperties.getChatTemperature())
                .maxTokens(dongragAiProperties.getChatMaxTokens())
                .build());
    }
}
