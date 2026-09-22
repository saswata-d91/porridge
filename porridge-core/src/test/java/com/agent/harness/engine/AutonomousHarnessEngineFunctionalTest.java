package com.agent.harness.engine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = {com.agent.harness.ClaudeCodeJavaHarnessApplication.class})
@ActiveProfiles("test")
public class AutonomousHarnessEngineFunctionalTest {

    @Autowired
    private ApplicationContext context;

    @org.springframework.boot.test.mock.mockito.MockBean(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private org.springframework.ai.chat.client.ChatClient.Builder chatClientBuilder;

    @Test
    public void contextLoadsAndEngineIsRegistered() {
        // This functional test verifies that the Spring Boot application context successfully starts up
        // and that all the @Configuration, @Component, and @Service beans are correctly wired,
        // simulating a full application startup without hanging the REPL.
        assertNotNull(context, "Spring ApplicationContext should not be null");
        
        AutonomousHarnessEngine engine = context.getBean(AutonomousHarnessEngine.class);
        assertNotNull(engine, "AutonomousHarnessEngine should be registered in context");
        
        SubagentManager subagentManager = context.getBean(SubagentManager.class);
        assertNotNull(subagentManager, "SubagentManager should be registered in context");
    }
}
