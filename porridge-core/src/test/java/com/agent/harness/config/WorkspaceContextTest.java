package com.agent.harness.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

public class WorkspaceContextTest {

    @BeforeEach
    public void setup() {
        WorkspaceContext.clear();
    }

    @AfterEach
    public void teardown() {
        WorkspaceContext.clear();
    }

    @Test
    public void testDefaultContext() {
        Path defaultPath = WorkspaceContext.getBaseDir();
        assertEquals(Paths.get("").toAbsolutePath(), defaultPath);
    }

    @Test
    public void testSetAndResolve() {
        Path sandboxPath = Paths.get("/tmp/sandbox-123");
        WorkspaceContext.setBaseDir(sandboxPath);
        
        assertEquals(sandboxPath, WorkspaceContext.getBaseDir());
        
        Path resolved = WorkspaceContext.resolve("test.txt");
        assertEquals(sandboxPath.resolve("test.txt").normalize(), resolved);
    }
    
    @Test
    public void testResolveAbsolutePathsWithinSandbox() {
        Path sandboxPath = Paths.get("/tmp/sandbox-123");
        WorkspaceContext.setBaseDir(sandboxPath);
        
        // Simulating a tool passing an absolute path that is inside the sandbox
        Path absPath = sandboxPath.resolve("test.txt");
        Path resolved = WorkspaceContext.resolve(absPath.toString());
        assertEquals(absPath, resolved);
    }
}
