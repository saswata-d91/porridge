package com.agent.harness.engine;

import com.agent.harness.memory.ContextPersistenceManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class SubagentManager {

    private final ChatClient chatClient;
    private final InMemoryChatMemory memory;
    private final ContextPersistenceManager persistenceManager;
    private final com.agent.harness.tools.DynamicWorkspaceLoader workspaceLoader;
    private final java.util.List<ToolCallback> mcpTools;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public SubagentManager(
            ChatClient.Builder clientBuilder,
            MessageChatMemoryAdvisor memoryAdvisor,
            InMemoryChatMemory memory,
            ContextPersistenceManager persistenceManager,
            com.agent.harness.tools.DynamicWorkspaceLoader workspaceLoader,
            com.agent.harness.tools.McpConnectionManager mcpManager) {
        this.chatClient = clientBuilder.defaultAdvisors(memoryAdvisor).build();
        this.memory = memory;
        this.persistenceManager = persistenceManager;
        this.workspaceLoader = workspaceLoader;
        this.mcpTools = mcpManager.loadMcpTools();
    }

    public String spawnSubagent(String prompt, String role, boolean sandbox) {
        String subagentId = "subagent-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("\n[SYSTEM] Forking subagent [" + subagentId + "] as role: " + role + (sandbox ? " (SANDBOXED)" : ""));
        
        Callable<String> subagentTask = () -> {
            java.nio.file.Path sandboxDir = null;
            if (sandbox) {
                sandboxDir = java.nio.file.Path.of(".porridge/sandboxes/" + subagentId).toAbsolutePath();
                try {
                    new ProcessBuilder("sh", "-c", "mkdir -p .porridge/sandboxes && git worktree add -b " + subagentId + " " + sandboxDir.toString())
                            .redirectErrorStream(true)
                            .start().waitFor();
                    com.agent.harness.config.WorkspaceContext.setBaseDir(sandboxDir);
                } catch (Exception e) {
                    return "Sandbox initialization failed: " + e.getMessage();
                }
            }

            try {
                persistenceManager.loadSessionFromDisk(subagentId, memory);
                
                String systemConstraints = workspaceLoader.gatherSkillsContext();
                String evaluationContext = "### Role:\n" + role + "\n\n### Local Workspace Constraints:\n" + systemConstraints + "\n\n### User Goal:\n" + prompt;

                String agentResponse = this.chatClient.prompt()
                        .user(evaluationContext)
                        .advisors(ctx -> ctx.param(MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, subagentId))
                        .functions("viewFileTool", "editFileTool", "replaceFileTool", "bashCommandTool", "grepSearchTool", "fetchUrlTool", "listDirectoryTool", "runBackgroundTaskTool", "checkTaskStatusTool", "addMemoryTool", "queryMemoryTool", "deleteMemoryTool", "orchestrateAgentTool")
                        .functions(mcpTools.toArray(new ToolCallback[0]))
                        .call()
                        .content();
                
                persistenceManager.saveSessionToDisk(subagentId, memory);
                return agentResponse;
            } finally {
                if (sandbox && sandboxDir != null) {
                    com.agent.harness.config.WorkspaceContext.clear();
                    try {
                        new ProcessBuilder("git", "worktree", "remove", "-f", sandboxDir.toString()).start().waitFor();
                        new ProcessBuilder("git", "branch", "-D", subagentId).start().waitFor();
                    } catch (Exception e) {}
                }
            }
        };

        try {
            Future<String> future = executor.submit(subagentTask);
            String result = future.get(); // Wait for subagent to finish
            System.out.println("\n[SYSTEM] Subagent [" + subagentId + "] finished.");
            return result;
        } catch (Exception e) {
            return "Subagent failed: " + e.getMessage();
        }
    }
}
