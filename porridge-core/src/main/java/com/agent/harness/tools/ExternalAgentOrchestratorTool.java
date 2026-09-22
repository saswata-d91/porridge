package com.agent.harness.tools;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.function.Function;

@Configuration
public class ExternalAgentOrchestratorTool {

    public record OrchestrateRequest(String agentType, String prompt, String targetDirectory) {}

    @Bean
    @Description("Delegate a complex task to another AI agent CLI like 'agy' (Antigravity), 'claude' (Claude Code), or 'codex'.")
    public Function<OrchestrateRequest, String> orchestrateAgentTool() {
        return req -> {
            try {
                String cmd;
                switch (req.agentType().toLowerCase()) {
                    case "agy":
                    case "antigravity":
                        // agy requires --goal for headless autonomous execution
                        cmd = "agy --print \"" + req.prompt().replace("\"", "\\\"") + "\"";
                        break;
                    case "claude":
                    case "claude-code":
                        // claude requires -p for non-interactive prompt execution
                        cmd = "claude -p \"" + req.prompt().replace("\"", "\\\"") + "\"";
                        break;
                    case "codex":
                        cmd = "codex --task \"" + req.prompt().replace("\"", "\\\"") + "\"";
                        break;
                    case "auggie":
                        cmd = "auggie \"" + req.prompt().replace("\"", "\\\"") + "\"";
                        break;
                    case "gh":
                    case "github":
                        cmd = "gh copilot -p \"" + req.prompt().replace("\"", "\\\"") + "\"";
                        break;
                    default:
                        return "Error: Unknown agent type. Use 'agy', 'claude', 'codex', 'auggie', or 'gh'.";
                }

                Path dir = req.targetDirectory() != null && !req.targetDirectory().trim().isEmpty() 
                        ? com.agent.harness.config.WorkspaceContext.resolve(req.targetDirectory()) 
                        : com.agent.harness.config.WorkspaceContext.getBaseDir();

                System.out.println("\n\u001B[33m[ORCHESTRATOR] Delegating task to " + req.agentType() + "...\u001B[0m");

                Process process = new ProcessBuilder("sh", "-c", cmd)
                        .directory(dir.toFile())
                        .redirectErrorStream(true)
                        .start();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    // Stream output to terminal so the user isn't stuck waiting blindly
                    System.out.println("  [" + req.agentType() + "] " + line);
                    output.append(line).append("\n");
                }
                process.waitFor();
                
                return "External agent " + req.agentType() + " finished with exit code " + process.exitValue() + ".\nOutput Summary:\n" + 
                        (output.length() > 5000 ? output.substring(output.length() - 5000) : output.toString());
            } catch (Exception e) {
                return "Failed to orchestrate external agent: " + e.getMessage();
            }
        };
    }
}
