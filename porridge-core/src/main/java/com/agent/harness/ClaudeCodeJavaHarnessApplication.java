package com.agent.harness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.agent.harness", "com.agent.graphmemory"})
public class ClaudeCodeJavaHarnessApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClaudeCodeJavaHarnessApplication.class, args);
    }
}
