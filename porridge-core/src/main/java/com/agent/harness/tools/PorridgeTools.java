package com.agent.harness.tools;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import java.io.*;
import java.nio.file.*;
import java.util.Scanner;
import java.util.function.Function;

@Configuration
public class PorridgeTools {

    public record ViewRequest(String path) {}
    public record EditRequest(String path, String content) {}
    public record ReplaceRequest(String path, String targetContent, String replacementContent) {}
    public record BashRequest(String command) {}
    public record SubagentRequest(String prompt, String role, boolean sandbox) {}
    public record GrepRequest(String directory, String query) {}
    public record FetchUrlRequest(String url) {}
    public record ListDirRequest(String path) {}
    public record AskHumanRequest(String question, java.util.List<String> options) {}
    public record BackgroundTaskRequest(String command) {}
    public record TaskStatusRequest(String taskId) {}

    private final java.util.Map<String, Process> activeTasks = new java.util.concurrent.ConcurrentHashMap<>();

    // 1. VIEW FILE WITH AUTOMATIC TOKEN TRIMMING
    @Bean
    @Description("View the code contents of a file at a specific path.")
    public Function<ViewRequest, String> viewFileTool() {
        return req -> {
            try {
                Path path = com.agent.harness.config.WorkspaceContext.resolve(req.path);
                long bytes = Files.size(path);
                
                // Sliding window threshold: Max 150KB files allowed directly into prompt context
                if (bytes > 150_000) {
                    return "Error: File is too large (" + bytes + " bytes). Please use explicit grep search to look for code blocks.";
                }
                return Files.readString(path);
            } catch (Exception e) { return "Error reading file: " + e.getMessage(); }
        };
    }

    // 2. SURGICAL EDIT TOOL
    @Bean
    @Description("Write or completely replace contents of a source code file at a path. Use cautiously on large files.")
    public Function<EditRequest, String> editFileTool(com.agent.harness.config.HarnessState harnessState) {
        return req -> {
            try {
                if (!harnessState.isDangerouslySkipPermissions()) {
                    System.out.print("\n[!] [GATEKEEPER] Agent wants to modify '" + req.path + "'. Allow? (y/N): ");
                    Scanner scanner = new Scanner(System.in);
                    String approval = scanner.nextLine();
                    if (!"y".equalsIgnoreCase(approval.trim())) {
                        return "Write Rejected: User denied write permission to this directory path.";
                    }
                }
                java.nio.file.Path target = com.agent.harness.config.WorkspaceContext.resolve(req.path);
                Files.writeString(target, req.content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return "Successfully updated file: " + req.path;
            } catch (Exception e) { return "Write failed: " + e.getMessage(); }
        };
    }

    // 2.5. AST-LIKE BLOCK REPLACEMENT TOOL
    @Bean
    @Description("Surgically replace a specific exact block of text in a file without rewriting the whole file.")
    public Function<ReplaceRequest, String> replaceFileTool(com.agent.harness.config.HarnessState harnessState) {
        return req -> {
            try {
                Path path = com.agent.harness.config.WorkspaceContext.resolve(req.path);
                if (!Files.exists(path)) return "Error: File does not exist.";
                
                String content = Files.readString(path);
                if (!content.contains(req.targetContent)) {
                    return "Error: targetContent not found exactly as written in the file. Watch out for indentation and hidden characters.";
                }

                if (!harnessState.isDangerouslySkipPermissions()) {
                    System.out.print("\n[!] [GATEKEEPER] Agent wants to run a surgical edit on '" + req.path + "'. Allow? (y/N): ");
                    Scanner scanner = new Scanner(System.in);
                    if (!"y".equalsIgnoreCase(scanner.nextLine().trim())) {
                        return "Write Rejected: User denied write permission.";
                    }
                }

                String updated = content.replace(req.targetContent, req.replacementContent);
                Files.writeString(path, updated, StandardOpenOption.TRUNCATE_EXISTING);
                return "Successfully replaced the requested block in " + req.path;
            } catch (Exception e) { return "Replace failed: " + e.getMessage(); }
        };
    }

    // 3. SECURE BASH ENGINE
    @Bean
    @Description("Run terminal commands like compiling code, testing, or checking git statuses.")
    public Function<BashRequest, String> bashCommandTool(com.agent.harness.config.HarnessState harnessState) {
        return req -> {
            String cmd = req.command.trim();
            
            // Block structural security threats even if permissions are skipped
            if (cmd.contains("rm -rf") || cmd.contains("sudo")) {
                return "Execution Blocked: Command violates terminal sandbox policy rules.";
            }

            if (!harnessState.isDangerouslySkipPermissions()) {
                System.out.print("\n[!] [GATEKEEPER] Agent wants to run bash command: `" + cmd + "`. Allow? (y/N): ");
                Scanner scanner = new Scanner(System.in);
                String approval = scanner.nextLine();
                if (!"y".equalsIgnoreCase(approval.trim())) {
                    return "Execution Refused: Human operator blocked terminal command execution.";
                }
            }

            try {
                boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
                Process process = new ProcessBuilder(isWindows ? new String[]{"cmd.exe", "/c", cmd} : new String[]{"sh", "-c", cmd})
                        .directory(com.agent.harness.config.WorkspaceContext.getBaseDir().toFile())
                        .redirectErrorStream(true)
                        .start();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                int lineCount = 0;

                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    lineCount++;
                    // Token Optimizer: Cap massive terminal dump loops (like long npm or maven stack traces)
                    if (lineCount > 100) {
                        output.append("\n...[Truncated by Harness Context Manager to protect Token Limit]...\n");
                        break;
                    }
                }
                return output.toString();
            } catch (Exception e) { return "Command execution failed: " + e.getMessage(); }
        };
    }

    // 4. MULTI-AGENT FORKING
    @Bean
    @Description("Spawns an autonomous subagent to perform a background task or research. Returns the final result.")
    public Function<SubagentRequest, String> spawnSubagentTool(com.agent.harness.engine.SubagentManager subagentManager) {
        return req -> subagentManager.spawnSubagent(req.prompt, req.role, req.sandbox);
    }

    // 5. GREP SEARCH TOOL
    @Bean
    @Description("Search the codebase for a specific string or regex pattern using grep.")
    public Function<GrepRequest, String> grepSearchTool() {
        return req -> {
            try {
                String cmd = "grep -rnw '" + req.directory + "' -e '" + req.query + "'";
                Process process = new ProcessBuilder("sh", "-c", cmd)
                        .directory(com.agent.harness.config.WorkspaceContext.getBaseDir().toFile())
                        .redirectErrorStream(true)
                        .start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (count++ > 50) {
                        output.append("... [Truncated due to too many results] ...\n");
                        break;
                    }
                }
                return output.toString().isEmpty() ? "No matches found." : output.toString();
            } catch (Exception e) { return "Grep failed: " + e.getMessage(); }
        };
    }

    // 6. FETCH URL TOOL
    @Bean
    @Description("Fetch and read the raw text content of a URL (useful for reading documentation).")
    public Function<FetchUrlRequest, String> fetchUrlTool() {
        return req -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(req.url))
                        .GET().build();
                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                // Extremely naive HTML strip
                body = body.replaceAll("<[^>]*>", " ");
                return body.length() > 10000 ? body.substring(0, 10000) + "...\n[Truncated]" : body;
            } catch (Exception e) { return "Fetch failed: " + e.getMessage(); }
        };
    }

    // 7. LIST DIRECTORY TOOL
    @Bean
    @Description("List the files and folders inside a given directory.")
    public Function<ListDirRequest, String> listDirectoryTool() {
        return req -> {
            try {
                Path dir = com.agent.harness.config.WorkspaceContext.resolve(req.path);
                if (!Files.exists(dir)) return "Error: Directory does not exist.";
                StringBuilder out = new StringBuilder("Contents of ").append(req.path).append(":\n");
                Files.list(dir).forEach(p -> out.append(p.getFileName()).append(Files.isDirectory(p) ? "/" : "").append("\n"));
                return out.toString();
            } catch (Exception e) { return "List failed: " + e.getMessage(); }
        };
    }
    // 8. INTERACTIVE ASK HUMAN TOOL
    @Bean
    @Description("Ask the human user a clarifying multiple-choice question and wait for their answer.")
    public Function<AskHumanRequest, String> askHumanTool() {
        return req -> {
            try {
                org.jline.terminal.Terminal terminal = org.jline.terminal.TerminalBuilder.builder().system(true).build();
                org.jline.reader.LineReader lineReader = org.jline.reader.LineReaderBuilder.builder().terminal(terminal).build();
                
                terminal.writer().println("\n\u001B[35m[AGENT QUESTION]: " + req.question() + "\u001B[0m");
                if (req.options() != null) {
                    for (int i = 0; i < req.options().size(); i++) {
                        terminal.writer().println((i + 1) + ") " + req.options().get(i));
                    }
                }
                terminal.writer().flush();
                
                String answer = lineReader.readLine("\u001B[36m[Select Option or type answer]: \u001B[0m");
                return "User responded: " + answer;
            } catch (Exception e) { return "Failed to ask question: " + e.getMessage(); }
        };
    }

    // 9. ASYNC BACKGROUND TASK TOOL
    @Bean
    @Description("Run a long-running bash command in the background. Returns a task ID immediately.")
    public Function<BackgroundTaskRequest, String> runBackgroundTaskTool() {
        return req -> {
            try {
                String taskId = "task-" + java.util.UUID.randomUUID().toString().substring(0, 6);
                Process process = new ProcessBuilder("sh", "-c", req.command())
                        .directory(com.agent.harness.config.WorkspaceContext.getBaseDir().toFile())
                        .start();
                activeTasks.put(taskId, process);
                return "Background task started with ID: " + taskId + ". Use checkTaskStatusTool to check it later.";
            } catch (Exception e) { return "Failed to start task: " + e.getMessage(); }
        };
    }

    // 10. CHECK TASK STATUS TOOL
    @Bean
    @Description("Check the status of a background task.")
    public Function<TaskStatusRequest, String> checkTaskStatusTool() {
        return req -> {
            Process p = activeTasks.get(req.taskId());
            if (p == null) return "Task ID not found.";
            if (p.isAlive()) return "Task is still running.";
            return "Task finished with exit code: " + p.exitValue();
        };
    }
}
