package com.agent.graphmemory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

public class GraphMemoryToolsTest {

    private GraphMemoryTools tools;
    private Path tempMemoryFile;

    @BeforeEach
    public void setup() throws Exception {
        tools = new GraphMemoryTools();
        tempMemoryFile = Paths.get(".porridge/graph_memory.json");
        Files.createDirectories(tempMemoryFile.getParent());
        if (Files.exists(tempMemoryFile)) {
            Files.delete(tempMemoryFile);
        }
    }

    @AfterEach
    public void teardown() throws Exception {
        if (Files.exists(tempMemoryFile)) {
            Files.delete(tempMemoryFile);
        }
    }

    @Test
    public void testAddAndQueryMemory() throws Exception {
        GraphMemoryTools.AddMemoryRequest addReq = new GraphMemoryTools.AddMemoryRequest("test-topic", "This is a test fact");
        String addResult = tools.addMemoryTool().apply(addReq);
        assertTrue(addResult.contains("Successfully added observation"), "Should successfully learn memory");

        GraphMemoryTools.QueryMemoryRequest queryReq = new GraphMemoryTools.QueryMemoryRequest("test-topic");
        String queryResult = tools.queryMemoryTool().apply(queryReq);
        assertTrue(queryResult.contains("This is a test fact"), "Query should return the learned fact");
    }

    @Test
    public void testDeleteMemory() throws Exception {
        tools.addMemoryTool().apply(new GraphMemoryTools.AddMemoryRequest("test-delete", "Fact to delete"));
        
        String queryResult = tools.queryMemoryTool().apply(new GraphMemoryTools.QueryMemoryRequest("test-delete"));
        assertTrue(queryResult.contains("Fact to delete"));
        
        GraphMemoryTools.DeleteMemoryRequest delReq = new GraphMemoryTools.DeleteMemoryRequest("test-delete", "Fact to delete");
        String delResult = tools.deleteMemoryTool().apply(delReq);
        assertTrue(delResult.contains("Successfully unlearned"), "Should successfully delete memory");
        
        String queryResultAfter = tools.queryMemoryTool().apply(new GraphMemoryTools.QueryMemoryRequest("test-delete"));
        assertFalse(queryResultAfter.contains("Fact to delete"), "Query should not contain deleted fact");
    }
}
