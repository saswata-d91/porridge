package com.agent.harness.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class McpConnectionManager {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<McpSyncClient> activeClients = new ArrayList<>();

    private static final String[] CONFIG_PATHS = {
        ".porridge/mcp.json",
        ".claude.json", // Claude Code
        System.getProperty("user.home") + "/.gemini/antigravity-cli/mcp.json", // Antigravity / Gemini MCP
        System.getProperty("user.home") + "/Library/Application Support/Claude/claude_desktop_config.json", // Claude Desktop Mac
        System.getenv("APPDATA") != null ? System.getenv("APPDATA") + "/Claude/claude_desktop_config.json" : null // Claude Desktop Win
    };

    public List<org.springframework.ai.tool.ToolCallback> loadMcpTools() {
        List<org.springframework.ai.tool.ToolCallback> allTools = new ArrayList<>();
        
        for (String configPath : CONFIG_PATHS) {
            if (configPath == null) continue;
            Path path = Path.of(configPath);
            if (Files.exists(path)) {
                System.out.println("[MCP] Loading config from " + path);
                allTools.addAll(parseConfigAndConnect(path));
            }
        }
        
        return allTools;
    }

    private List<org.springframework.ai.tool.ToolCallback> parseConfigAndConnect(Path configPath) {
        List<org.springframework.ai.tool.ToolCallback> tools = new ArrayList<>();
        try {
            JsonNode rootNode = objectMapper.readTree(configPath.toFile());
            JsonNode mcpServersNode = rootNode.path("mcpServers");
            
            if (mcpServersNode.isObject()) {
                mcpServersNode.fields().forEachRemaining(entry -> {
                    String serverName = entry.getKey();
                    JsonNode serverConfig = entry.getValue();
                    
                    String command = serverConfig.path("command").asText();
                    if (command == null || command.isEmpty()) return;
                    
                    List<String> args = new ArrayList<>();
                    serverConfig.path("args").forEach(argNode -> args.add(argNode.asText()));
                    
                    Map<String, String> env = new HashMap<>();
                    JsonNode envNode = serverConfig.path("env");
                    if (envNode.isObject()) {
                        envNode.fields().forEachRemaining(e -> env.put(e.getKey(), e.getValue().asText()));
                    }

                    try {
                        ServerParameters params = ServerParameters.builder(command)
                                .args(args.toArray(new String[0]))
                                .env(env)
                                .build();
                        
                        McpClientTransport transport = new StdioClientTransport(params);
                        McpSyncClient client = McpClient.sync(transport).build();
                        client.initialize();
                        
                        activeClients.add(client);
                        
                        SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(client);
                        org.springframework.ai.tool.ToolCallback[] serverTools = provider.getToolCallbacks();
                        tools.addAll(java.util.Arrays.asList(serverTools));
                        System.out.println("[MCP] Connected to server: " + serverName + " (Loaded " + serverTools.length + " tools)");
                    } catch (Exception ex) {
                        System.err.println("[X] Failed to connect to MCP server: " + serverName + " - " + ex.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("[X] Failed to parse MCP config: " + configPath + " - " + e.getMessage());
        }
        return tools;
    }
}
