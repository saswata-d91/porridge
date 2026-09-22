package com.agent.harness.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ContextPersistenceManager {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String getHistoryPath(String conversationId) {
        return ".harness/history_" + conversationId + ".json";
    }

    public void saveSessionToDisk(String conversationId, InMemoryChatMemory memory) {
        try {
            List<Message> currentHistory = memory.get(conversationId, 100);
            File destination = new File(getHistoryPath(conversationId));
            
            if (!destination.getParentFile().exists()) {
                destination.getParentFile().mkdirs();
            }

            objectMapper.writeValue(destination, currentHistory);
            System.out.println("\n[SAVE] Context safely persisted to " + destination.getPath());
        } catch (IOException e) {
            System.err.println("[X] Failed to cache execution history: " + e.getMessage());
        }
    }

    public void loadSessionFromDisk(String conversationId, InMemoryChatMemory memory) {
        File file = new File(getHistoryPath(conversationId));
        if (!file.exists()) return;

        try {
            CollectionType listType = objectMapper.getTypeFactory().constructCollectionType(ArrayList.class, Message.class);
            List<Message> savedMessages = objectMapper.readValue(file, listType);
            memory.add(conversationId, savedMessages);
            System.out.println("[RESTORE] Restored workspace state for " + conversationId);
        } catch (IOException e) {
            System.err.println("[!] Context cache corrupted for " + conversationId + ". Starting clean workspace.");
        }
    }
}
