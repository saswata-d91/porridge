package com.agent.harness.tools;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;

@Component
public class DynamicWorkspaceLoader {

    public record ScriptTool(String name, String path) {}

    @Value("${porridge.instruction-files:PORRIDGE.md}")
    private List<String> instructionFiles;

    @Value("${porridge.skills-dir:.porridge/skills}")
    private String skillsDirName;

    @Value("${porridge.tools-dir:.porridge/tools}")
    private String toolsDirName;

    /**
     * Walks up the directory tree to find all skill markdown files
     * in .porridge/skills/ and PORRIDGE.md files.
     */
    public String gatherSkillsContext() {
        StringBuilder context = new StringBuilder();
        Path current = Path.of(".").toAbsolutePath().normalize();
        
        while (current != null) {
            // Read base instruction files from config
            if (instructionFiles != null) {
                for (String fileName : instructionFiles) {
                    Path instructionPath = current.resolve(fileName.trim());
                    if (Files.exists(instructionPath)) {
                        context.append("### Context from ").append(instructionPath).append(":\n")
                               .append(readSafely(instructionPath)).append("\n\n");
                    }
                }
            }
            
            // Read skill files from configured skills directory
            Path skillsDir = current.resolve(skillsDirName);
            if (Files.exists(skillsDir) && Files.isDirectory(skillsDir)) {
                try (Stream<Path> paths = Files.walk(skillsDir, 1)) {
                    paths.filter(Files::isRegularFile)
                         .filter(p -> p.toString().endsWith(".md"))
                         .forEach(p -> {
                             context.append("### Skill: ").append(p.getFileName()).append("\n")
                                    .append(readSafely(p)).append("\n\n");
                         });
                } catch (Exception e) {
                    System.err.println("Failed to read skills directory: " + skillsDir);
                }
            }
            current = current.getParent();
        }
        
        String finalContext = context.toString().trim();
        return finalContext.isEmpty() ? "No local project constraints provided." : finalContext;
    }

    /**
     * Walks up the directory tree to find executable tools in .porridge/tools/
     */
    public List<ScriptTool> discoverDynamicTools() {
        List<ScriptTool> tools = new ArrayList<>();
        Path current = Path.of(".").toAbsolutePath().normalize();
        
        while (current != null) {
            Path toolsDir = current.resolve(toolsDirName);
            if (Files.exists(toolsDir) && Files.isDirectory(toolsDir)) {
                try (Stream<Path> paths = Files.walk(toolsDir, 1)) {
                    paths.filter(Files::isRegularFile)
                         .filter(Files::isExecutable)
                         .forEach(p -> {
                             String name = p.getFileName().toString().replace(".sh", "").replace(".py", "");
                             tools.add(new ScriptTool(name, p.toString()));
                         });
                } catch (Exception e) {
                    System.err.println("Failed to read tools directory: " + toolsDir);
                }
            }
            current = current.getParent();
        }
        return tools;
    }

    private String readSafely(Path p) {
        try {
            return Files.readString(p);
        } catch (Exception e) {
            return "";
        }
    }
}
