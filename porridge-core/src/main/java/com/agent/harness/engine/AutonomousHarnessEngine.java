package com.agent.harness.engine;
import com.agent.harness.config.HarnessState;
import com.agent.harness.config.WorkspaceContext;
import com.agent.harness.memory.ContextPersistenceManager;
import com.agent.harness.tools.DynamicWorkspaceLoader;
import com.agent.harness.tools.McpConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;











@Component
public class AutonomousHarnessEngine implements CommandLineRunner {

    private final ChatClient chatClient;
    private final InMemoryChatMemory memory;
    private final ContextPersistenceManager persistenceManager;
    private final DynamicWorkspaceLoader workspaceLoader;
    private final McpConnectionManager mcpManager;
    private final HarnessState harnessState;
    private final SubagentManager subagentManager;
    private List<ToolCallback> mcpTools;

    public AutonomousHarnessEngine(
            ChatClient.Builder clientBuilder, 
            MessageChatMemoryAdvisor memoryAdvisor,
            InMemoryChatMemory memory,
            ContextPersistenceManager persistenceManager,
            DynamicWorkspaceLoader workspaceLoader,
            McpConnectionManager mcpManager,
            HarnessState harnessState,
            SubagentManager subagentManager) {
        
        this.memory = memory;
        this.persistenceManager = persistenceManager;
        this.workspaceLoader = workspaceLoader;
        this.mcpManager = mcpManager;
        this.harnessState = harnessState;
        this.subagentManager = subagentManager;
        
        System.out.println("[AGENT] Initializing MCP connections...");
        this.mcpTools = mcpManager.loadMcpTools();
        this.chatClient = clientBuilder
                .defaultAdvisors(memoryAdvisor)
                .defaultSystem("""
                    You are Porridge, an elite terminal automation engineer.
                    You have read access to codebase files and execution abilities via tools.
                    When solving coding problems, you must locate files, make edits, and run commands 
                    to compile and test your changes before claiming the issue is solved.
                    """)
                .build();
    }

    @Autowired
    private Environment env;

    @Override
    public void run(String... args) throws Exception {
        if (Arrays.asList(env.getActiveProfiles()).contains("test")) {
            return;
        }

        if (Arrays.asList(args).contains("--mcp-server")) {
            System.err.println("[SYSTEM] Starting Porridge in MCP Server mode over stdio...");
            startMcpServer();
            return;
        }

        org.jline.terminal.Terminal terminal = org.jline.terminal.TerminalBuilder.builder().system(true).build();
        org.jline.reader.LineReader lineReader = org.jline.reader.LineReaderBuilder.builder().terminal(terminal).build();

        String mainSessionId = "main-session";
        
        // 1. Hydrate memory context from the local cache file
        persistenceManager.loadSessionFromDisk(mainSessionId, memory);

        
        terminal.writer().println("\n\u001B[32m[AGENT]\u001B[0m Porridge Initialized.");
        terminal.writer().println("Type your requirement or 'exit' to cleanly close the session.");
        terminal.writer().flush();

        while (true) {
            String prompt;
            try {
                prompt = lineReader.readLine("\n\u001B[36m[USER]:\u001B[0m ");
            } catch (org.jline.reader.UserInterruptException | org.jline.reader.EndOfFileException e) {
                prompt = "exit";
            }
            
            if ("exit".equalsIgnoreCase(prompt.trim())) {
                // 2. Persist workspace context map safely to disk before JVM shutdown
                persistenceManager.saveSessionToDisk(mainSessionId, memory);
                terminal.writer().println("Goodbye!");
                terminal.writer().flush();
                break;
            }

            if (prompt.trim().startsWith("/")) {
                if (prompt.trim().startsWith("/image ")) {
                    // Vision processing passes through to the agent, handled below
                } else {
                    handleSlashCommand(prompt.trim(), terminal, lineReader);
                    continue;
                }
            }

            String systemConstraints = workspaceLoader.gatherSkillsContext();
            String evaluationContext = "### Local Workspace Constraints:\n" + systemConstraints + "\n\n";
            evaluationContext += "### Artifacts & Plans Directory:\nWhen asked to generate plans, reports, or persistent artifacts, save them in the following conversation-specific directory: " + harnessState.getArtifactsDir() + "/" + mainSessionId + ". You must create this directory if it doesn't exist before saving files to it.\n\n";

            if (harnessState.isPlanMode()) {
                evaluationContext += "### CRITICAL INSTRUCTION:\nYou are in PLAN MODE. You must ONLY output a markdown plan. DO NOT invoke any tools except to view files. DO NOT modify files or run bash commands.\n\n";
            }
            
            List<Media> mediaAttachments = new ArrayList<>();
            String[] tokens = prompt.split("\\s+");
            for (String token : tokens) {
                try {
                    Path p = Path.of(token);
                    if (Files.exists(p) && !Files.isDirectory(p)) {
                        String lowerPath = token.toLowerCase();
                        String mimeStr = null;
                        if (lowerPath.endsWith(".png")) mimeStr = "image/png";
                        else if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) mimeStr = "image/jpeg";
                        else if (lowerPath.endsWith(".gif")) mimeStr = "image/gif";
                        else if (lowerPath.endsWith(".webp")) mimeStr = "image/webp";
                        else if (lowerPath.endsWith(".mp4")) mimeStr = "video/mp4";
                        else if (lowerPath.endsWith(".mpeg")) mimeStr = "video/mpeg";
                        else if (lowerPath.endsWith(".mov")) mimeStr = "video/quicktime";
                        else if (lowerPath.endsWith(".mp3")) mimeStr = "audio/mpeg";
                        else if (lowerPath.endsWith(".wav")) mimeStr = "audio/wav";
                        else if (lowerPath.endsWith(".ogg")) mimeStr = "audio/ogg";
                        
                        if (mimeStr != null) {
                            byte[] fileBytes = Files.readAllBytes(p);
                            MimeType mimeType = MimeType.valueOf(mimeStr);
                            mediaAttachments.add(new Media(mimeType, new ByteArrayResource(fileBytes)));
                            terminal.writer().println("\u001B[32m[SYSTEM] Auto-attached media: " + token + "\u001B[0m");
                        }
                    }
                } catch (Exception e) {
                    // Ignore, not a valid path or couldn't read
                }
            }
            
            UserMessage userMessage;
            if (!mediaAttachments.isEmpty()) {
                userMessage = new UserMessage(evaluationContext + "### User Goal:\n" + prompt, mediaAttachments);
            } else {
                userMessage = new UserMessage(evaluationContext + "### User Goal:\n" + prompt);
            }

            // 3. Process instructions through the ReAct engine loop
            String agentResponse;
            String engine = harnessState.getExecutionEngine();
            if (!"porridge".equalsIgnoreCase(engine)) {
                terminal.writer().println("\u001B[33m[SYSTEM] Delegating execution to local engine: " + engine + "...\u001B[0m");
                terminal.writer().flush();
                String extModel = harnessState.getCurrentModel();
                String cliCmd = "";
                
                if (engine.equalsIgnoreCase("agy")) {
                    cliCmd = "agy --goal \"" + prompt.replace("\"", "\\\"") + "\"" + (!extModel.equals("default") ? " --model " + extModel : "");
                } else if (engine.equalsIgnoreCase("claude-code") || engine.equalsIgnoreCase("claude")) {
                    // Claude Code accepts model via environment variables usually, but we can try --model if they ever add it, or just pass nothing.
                    cliCmd = "claude -p \"" + prompt.replace("\"", "\\\"") + "\"" + (!extModel.equals("default") ? " -m " + extModel : "");
                } else if (engine.equalsIgnoreCase("codex")) {
                    cliCmd = "codex --task \"" + prompt.replace("\"", "\\\"") + "\"" + (!extModel.equals("default") ? " --model " + extModel : "");
                } else if (engine.equalsIgnoreCase("auggie")) {
                    cliCmd = "auggie \"" + prompt.replace("\"", "\\\"") + "\"" + (!extModel.equals("default") ? " --model " + extModel : "");
                } else if (engine.equalsIgnoreCase("gh") || engine.equalsIgnoreCase("github")) {
                    cliCmd = "gh copilot suggest -t shell \"" + prompt.replace("\"", "\\\"") + "\"";
                } else {
                    cliCmd = engine + " \"" + prompt.replace("\"", "\\\"") + "\"";
                }

                try {
                    Process process = new ProcessBuilder("sh", "-c", cliCmd)
                            .directory(WorkspaceContext.getBaseDir().toFile())
                            .redirectErrorStream(true)
                            .start();
                    
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        terminal.writer().println("  [" + engine + "] " + line);
                        terminal.writer().flush();
                        output.append(line).append("\n");
                    }
                    process.waitFor();
                    agentResponse = "[Delegated to " + engine + "]\n" + output.toString();
                    
                    // Manually push to history so Porridge retains the context across turns
                    memory.add(mainSessionId, List.of(userMessage));
                    memory.add(mainSessionId, List.of(new AssistantMessage(agentResponse)));
                    
                } catch (Exception e) {
                    agentResponse = "Execution via " + engine + " failed: " + e.getMessage();
                }
            } else {
                agentResponse = this.chatClient.prompt()
                        .messages(userMessage)
                        .advisors(ctx -> ctx.param(MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, mainSessionId))
                        .functions("viewFileTool", "editFileTool", "replaceFileTool", "bashCommandTool", "spawnSubagentTool", "grepSearchTool", "fetchUrlTool", "listDirectoryTool", "askHumanTool", "runBackgroundTaskTool", "checkTaskStatusTool", "addMemoryTool", "queryMemoryTool", "deleteMemoryTool", "orchestrateAgentTool")
                        .functions(mcpTools.toArray(new ToolCallback[0]))
                        .call()
                        .content();
            }

            terminal.writer().println("\n\u001B[32m[AGENT]:\u001B[0m " + agentResponse);
            terminal.writer().flush();
        }
    }

    private void handleSlashCommand(String command, org.jline.terminal.Terminal terminal, org.jline.reader.LineReader lineReader) {
        String[] parts = command.split(" ", 2);
        String cmd = parts[0].toLowerCase();
        
        switch (cmd) {
            case "/mcp":
                if (parts.length > 1) {
                    String[] mcpArgs = parts[1].trim().split(" ", 2);
                    if (mcpArgs[0].equalsIgnoreCase("auth") && mcpArgs.length > 1) {
                        mcpManager.runAuthCommand(mcpArgs[1].trim());
                    } else if (mcpArgs[0].equalsIgnoreCase("reload")) {
                        terminal.writer().println("\u001B[33m[SYSTEM] Reloading all MCP servers...\u001B[0m");
                    } else {
                        terminal.writer().println("\u001B[31m[SYSTEM] Usage: /mcp auth <serverName> OR /mcp reload\u001B[0m");
                        break;
                    }
                    mcpManager.closeAll();
                    this.mcpTools = mcpManager.loadMcpTools();
                    terminal.writer().println("\u001B[32m[SYSTEM] MCP Servers reloaded successfully.\u001B[0m");
                } else {
                    terminal.writer().println("\u001B[31m[SYSTEM] Usage: /mcp auth <serverName> OR /mcp reload\u001B[0m");
                }
                break;
            case "/wizard":
                terminal.writer().println("\u001B[36m=== Configuration Wizard ===\u001B[0m");
                terminal.writer().println("1) Add a new Skill (Markdown Instructions)");
                terminal.writer().println("2) Add a new MCP Tool Server");
                terminal.writer().flush();
                String choice = lineReader.readLine("\u001B[33mSelect an option (1/2): \u001B[0m");
                
                if ("1".equals(choice.trim())) {
                    String name = lineReader.readLine("Skill Name (e.g. 'React Guidelines'): ");
                    String filename = name.toLowerCase().replace(" ", "_") + ".md";
                    String instructions = lineReader.readLine("Instructions for the agent: ");
                    
                    try {
                        Path skillFile = Path.of(".porridge/skills/" + filename);
                        if (!Files.exists(skillFile.getParent())) Files.createDirectories(skillFile.getParent());
                        Files.writeString(skillFile, "### " + name + "\n" + instructions);
                        terminal.writer().println("\u001B[32m[SYSTEM] Skill created at " + skillFile.toString() + "\u001B[0m");
                    } catch (Exception e) {
                        terminal.writer().println("\u001B[31m[SYSTEM] Failed to save skill.\u001B[0m");
                    }
                } else if ("2".equals(choice.trim())) {
                    String serverName = lineReader.readLine("Server Name (e.g. 'postgres'): ");
                    String execCommand = lineReader.readLine("Execution Command (e.g. 'npx'): ");
                    String argsStr = lineReader.readLine("Arguments (space separated, e.g. '-y @modelcontextprotocol/server-postgres'): ");
                    
                    try {
                        Path mcpFile = Path.of("mcp.json");
                        String json = "{\n  \"mcpServers\": {\n    \"" + serverName + "\": {\n      \"command\": \"" + execCommand + "\",\n      \"args\": [\"" + argsStr.replace(" ", "\", \"") + "\"]\n    }\n  }\n}";
                        Files.writeString(mcpFile, json, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        terminal.writer().println("\u001B[32m[SYSTEM] MCP server appended to mcp.json! Restart harness to apply.\u001B[0m");
                    } catch (Exception e) {
                        terminal.writer().println("\u001B[31m[SYSTEM] Failed to save MCP config.\u001B[0m");
                    }
                }
                break;
            case "/dangerously-skip-permissions":
                harnessState.setDangerouslySkipPermissions(true);
                terminal.writer().println("\u001B[33m[SYSTEM] Warning: Permissions checks disabled. Agent has raw write access.\u001B[0m");
                break;
            case "/plan":
                harnessState.setPlanMode(!harnessState.isPlanMode());
                terminal.writer().println("\u001B[34m[SYSTEM] Plan mode is now: " + (harnessState.isPlanMode() ? "ON" : "OFF") + "\u001B[0m");
                break;
            case "/engine":
                if (parts.length > 1) {
                    harnessState.setExecutionEngine(parts[1].trim());
                    terminal.writer().println("\u001B[35m[SYSTEM] Switched execution engine to: " + parts[1].trim() + "\u001B[0m");
                } else {
                    terminal.writer().println("\n\u001B[36m=== Engine Selection ===\u001B[0m");
                    terminal.writer().println("Available engines:");
                    terminal.writer().println("1) porridge (Native Spring AI, uses API keys)");
                    terminal.writer().println("2) agy (Google Antigravity CLI)");
                    terminal.writer().println("3) claude-code (Anthropic CLI)");
                    terminal.writer().println("4) auggie (Auggie CLI)");
                    terminal.writer().println("5) gh (GitHub Copilot CLI)");
                    terminal.writer().println("6) codex");
                    terminal.writer().flush();
                    
                    String engineChoice = lineReader.readLine("\u001B[32mSelect engine (1-6) [Current: " + harnessState.getExecutionEngine() + "]: \u001B[0m");
                    String newEngine = harnessState.getExecutionEngine();
                    switch (engineChoice.trim()) {
                        case "1": newEngine = "porridge"; break;
                        case "2": newEngine = "agy"; break;
                        case "3": newEngine = "claude-code"; break;
                        case "4": newEngine = "auggie"; break;
                        case "5": newEngine = "gh"; break;
                        case "6": newEngine = "codex"; break;
                    }
                    if (newEngine != null && !newEngine.isEmpty()) {
                        harnessState.setExecutionEngine(newEngine);
                        terminal.writer().println("\u001B[35m[SYSTEM] Switched execution engine to: " + newEngine + "\u001B[0m");
                    }
                }
                break;
            case "/model":
                if (parts.length > 1) {
                    harnessState.setCurrentModel(parts[1].trim());
                    terminal.writer().println("\u001B[34m[SYSTEM] Switched model to: " + parts[1].trim() + "\u001B[0m");
                } else {
                    terminal.writer().println("\n\u001B[36m=== Model Selection for Engine [" + harnessState.getExecutionEngine() + "] ===\u001B[0m");
                    String newModel = harnessState.getCurrentModel();
                    
                    if (harnessState.getExecutionEngine().equalsIgnoreCase("agy")) {
                        terminal.writer().println("1) default");
                        terminal.writer().println("2) gemini-3.1-pro");
                        terminal.writer().println("3) gemini-3.95-flash");
                        terminal.writer().println("4) gemini-3.7-flash");
                        terminal.writer().println("5) gemini-3.8-flash");
                        terminal.writer().println("6) gpt-oss");
                        terminal.writer().println("7) claude-sonnet");
                        terminal.writer().println("8) Custom / Manual Entry");
                        terminal.writer().flush();
                        
                        String modelChoice = lineReader.readLine("\u001B[32mSelect model (1-8) [Current: " + harnessState.getCurrentModel() + "]: \u001B[0m");
                        switch (modelChoice.trim()) {
                            case "1": newModel = "default"; break;
                            case "2": newModel = "gemini-3.1-pro"; break;
                            case "3": newModel = "gemini-3.95-flash"; break;
                            case "4": newModel = "gemini-3.7-flash"; break;
                            case "5": newModel = "gemini-3.8-flash"; break;
                            case "6": newModel = "gpt-oss"; break;
                            case "7": newModel = "claude-sonnet"; break;
                            case "8": newModel = lineReader.readLine("\u001B[32mEnter custom model name: \u001B[0m").trim(); break;
                        }
                    } else {
                        terminal.writer().println("1) default (Engine's default)");
                        terminal.writer().println("2) gemini-1.5-pro");
                        terminal.writer().println("3) gemini-1.5-flash");
                        terminal.writer().println("4) claude-3-5-sonnet-20240620");
                        terminal.writer().println("5) claude-3-opus-20240229");
                        terminal.writer().println("6) gpt-4o");
                        terminal.writer().println("7) gpt-4-turbo");
                        terminal.writer().println("8) llama3");
                        terminal.writer().println("9) Custom / Manual Entry");
                        terminal.writer().flush();
                        
                        String modelChoice = lineReader.readLine("\u001B[32mSelect model (1-9) [Current: " + harnessState.getCurrentModel() + "]: \u001B[0m");
                        switch (modelChoice.trim()) {
                            case "1": newModel = "default"; break;
                            case "2": newModel = "gemini-1.5-pro"; break;
                            case "3": newModel = "gemini-1.5-flash"; break;
                            case "4": newModel = "claude-3-5-sonnet-20240620"; break;
                            case "5": newModel = "claude-3-opus-20240229"; break;
                            case "6": newModel = "gpt-4o"; break;
                            case "7": newModel = "gpt-4-turbo"; break;
                            case "8": newModel = "llama3"; break;
                            case "9": newModel = lineReader.readLine("\u001B[32mEnter custom model name: \u001B[0m").trim(); break;
                        }
                    }
                    if (newModel != null && !newModel.isEmpty()) {
                        harnessState.setCurrentModel(newModel);
                        terminal.writer().println("\u001B[34m[SYSTEM] Switched model to: " + newModel + "\u001B[0m");
                    }
                }
                break;
            case "/learn":
                if (parts.length > 1) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        Path memoryFile = Path.of(".porridge/graph_memory.json");
                        if (!Files.exists(memoryFile.getParent()) && memoryFile.getParent() != null) {
                            Files.createDirectories(memoryFile.getParent());
                        }
                        Map<String, List<String>> memory = new HashMap<>();
                        if (Files.exists(memoryFile)) {
                            memory = mapper.readValue(memoryFile.toFile(), Map.class);
                        }
                        memory.putIfAbsent("global", new ArrayList<>());
                        memory.get("global").add(parts[1].trim());
                        mapper.writeValue(memoryFile.toFile(), memory);
                        
                        terminal.writer().println("\u001B[32m[SYSTEM] Fact learned and persisted to Semantic Graph Memory!\u001B[0m");
                    } catch (Exception e) {
                        terminal.writer().println("\u001B[31m[SYSTEM] Failed to write knowledge: " + e.getMessage() + "\u001B[0m");
                    }
                } else {
                    terminal.writer().println("\u001B[31m[SYSTEM] Usage: /learn <fact to remember>\u001B[0m");
                }
                break;
            case "/unlearn":
                if (parts.length > 1) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        Path memoryFile = Path.of(".porridge/graph_memory.json");
                        if (Files.exists(memoryFile)) {
                            Map<String, List<String>> memory = mapper.readValue(memoryFile.toFile(), Map.class);
                            boolean removed = false;
                            for (List<String> obs : memory.values()) {
                                if (obs.removeIf(o -> o.contains(parts[1].trim()))) removed = true;
                            }
                            mapper.writeValue(memoryFile.toFile(), memory);
                            if (removed) terminal.writer().println("\u001B[32m[SYSTEM] Unlearned matching facts from Semantic Graph Memory!\u001B[0m");
                            else terminal.writer().println("\u001B[33m[SYSTEM] No matching facts found to unlearn.\u001B[0m");
                        }
                    } catch (Exception e) {
                        terminal.writer().println("\u001B[31m[SYSTEM] Failed to remove knowledge: " + e.getMessage() + "\u001B[0m");
                    }
                } else {
                    terminal.writer().println("\u001B[31m[SYSTEM] Usage: /unlearn <fact to forget>\u001B[0m");
                }
                break;
            case "/summarize":
                terminal.writer().println("\u001B[33m[SYSTEM] Manual memory compaction triggered...\u001B[0m");
                String instruction = parts.length > 1 ? parts[1].trim() : "Summarize the key architectural decisions, facts, and goals from this conversation history.";
                
                List<Message> history = memory.get("main-session", 100);
                StringBuilder historyText = new StringBuilder();
                for (Message msg : history) {
                    historyText.append(msg.getMessageType()).append(": ").append(msg.getText()).append("\n");
                }
                
                String summary = chatClient.prompt()
                    .user("Given the following conversation history, " + instruction + "\n\nHistory:\n" + historyText.toString())
                    .call()
                    .content();
                    
                memory.clear("main-session");
                memory.add("main-session", List.of(new SystemMessage("Previous Session Summary: " + summary)));
                terminal.writer().println("\u001B[32m[SYSTEM] Memory compacted. New context established.\u001B[0m");
                terminal.writer().println("\nSummary:\n" + summary);
                break;
            default:
                terminal.writer().println("\u001B[31m[SYSTEM] Unknown command. Available: /wizard, /dangerously-skip-permissions, /plan, /engine <name>, /model <name>, /learn <fact>, /unlearn <fact>, /summarize [instructions]\u001B[0m");
        }
        terminal.writer().flush();
    }

    private void startMcpServer() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            McpSyncServer server = McpServer.sync(new StdioServerTransportProvider(mapper))
                .serverInfo("porridge-mcp-connector", "1.0.0")
                .tools(
                    new McpServerFeatures.SyncToolSpecification(
                        new McpSchema.Tool(
                            "invoke_porridge_agent",
                            "Delegates a task to the Porridge autonomous agent harness. The agent will run in an isolated sandbox, use its own semantic graph memory, skills, and tools, and return the final result.",
                            "{\"type\":\"object\",\"properties\":{\"prompt\":{\"type\":\"string\",\"description\":\"The detailed instructions for the task\"},\"role\":{\"type\":\"string\",\"description\":\"The role for the subagent, e.g. 'Senior Developer'\"},\"sandbox\":{\"type\":\"boolean\",\"description\":\"Whether to run in a temporary git worktree sandbox\"}},\"required\":[\"prompt\",\"role\",\"sandbox\"]}"
                        ),
                        (exchange, args) -> {
                            String prompt = (String) args.get("prompt");
                            String role = (String) args.get("role");
                            Boolean sandbox = (Boolean) args.get("sandbox");
                            if (sandbox == null) sandbox = true;
                            
                            System.err.println("[MCP] Received request to invoke agent: " + prompt);
                            String response = subagentManager.spawnSubagent(prompt, role, sandbox);
                            
                            return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(response)), 
                                false
                            );
                        }
                    )
                )
                .build();
            
            // Block forever listening for stdio json-rpc messages
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }
}
