package com.agent.graphmemory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

@Configuration
public class GraphMemoryTools {

    public record AddMemoryRequest(String entity, String observation) {}
    public record QueryMemoryRequest(String entity) {}
    public record DeleteMemoryRequest(String entity, String observation) {}

    private final Path memoryFile = Path.of(".porridge/graph_memory.json");
    private final ObjectMapper mapper = new ObjectMapper();

    @Bean
    @Description("Add a fact or observation about a specific entity (like a class, concept, or person) to the long-term memory graph.")
    public Function<AddMemoryRequest, String> addMemoryTool() {
        return req -> {
            try {
                Map<String, List<String>> memory = loadMemory();
                String key = req.entity().toLowerCase();
                memory.putIfAbsent(key, new ArrayList<>());
                if (!memory.get(key).contains(req.observation())) {
                    memory.get(key).add(req.observation());
                }
                saveMemory(memory);
                return "Successfully added observation to entity: " + req.entity();
            } catch (Exception e) {
                return "Failed to save memory: " + e.getMessage();
            }
        };
    }

    @Bean
    @Description("Query the long-term memory graph for facts about a specific entity.")
    public Function<QueryMemoryRequest, String> queryMemoryTool() {
        return req -> {
            try {
                Map<String, List<String>> memory = loadMemory();
                String key = req.entity().toLowerCase();
                List<String> observations = memory.get(key);
                
                StringBuilder results = new StringBuilder();
                if (observations != null && !observations.isEmpty()) {
                    results.append("Exact Memory for '").append(req.entity()).append("':\n");
                    observations.forEach(obs -> results.append(" - ").append(obs).append("\n"));
                }
                
                // Also search for partial graph matches
                for (Map.Entry<String, List<String>> entry : memory.entrySet()) {
                    if (!entry.getKey().equals(key) && (entry.getKey().contains(key) || key.contains(entry.getKey()))) {
                        results.append("\nRelated Entity '").append(entry.getKey()).append("':\n");
                        entry.getValue().forEach(obs -> results.append(" - ").append(obs).append("\n"));
                    }
                }
                
                if (results.length() > 0) return results.toString();
                return "No memory found for entity: " + req.entity();
            } catch (Exception e) {
                return "Failed to query memory: " + e.getMessage();
            }
        };
    }

    @Bean
    @Description("Delete a false or outdated fact/observation from the long-term memory graph. Only provide the exact observation text.")
    public Function<DeleteMemoryRequest, String> deleteMemoryTool() {
        return req -> {
            try {
                Map<String, List<String>> memory = loadMemory();
                String key = req.entity().toLowerCase();
                List<String> observations = memory.get(key);
                if (observations != null) {
                    boolean removed = observations.remove(req.observation());
                    if (observations.isEmpty()) memory.remove(key);
                    saveMemory(memory);
                    if (removed) return "Successfully unlearned observation from entity: " + req.entity();
                }
                return "Observation not found for entity: " + req.entity();
            } catch (Exception e) {
                return "Failed to delete memory: " + e.getMessage();
            }
        };
    }

    private Map<String, List<String>> loadMemory() throws Exception {
        if (!Files.exists(memoryFile)) return new HashMap<>();
        return mapper.readValue(memoryFile.toFile(), Map.class);
    }

    private void saveMemory(Map<String, List<String>> memory) throws Exception {
        if (!Files.exists(memoryFile.getParent()) && memoryFile.getParent() != null) {
            Files.createDirectories(memoryFile.getParent());
        }
        mapper.writeValue(memoryFile.toFile(), memory);
    }
}
