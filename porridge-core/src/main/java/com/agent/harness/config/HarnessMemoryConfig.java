package com.agent.harness.config;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HarnessMemoryConfig {

    @Bean
    public InMemoryChatMemory runtimeChatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean
    public MessageChatMemoryAdvisor chatMemoryAdvisor(InMemoryChatMemory runtimeChatMemory) {
        // Keeps a sliding window of the last 100 messages to protect token limits.
        // The conversation ID is overridden dynamically per-request.
        return new MessageChatMemoryAdvisor(runtimeChatMemory, "default", 100);
    }
}
