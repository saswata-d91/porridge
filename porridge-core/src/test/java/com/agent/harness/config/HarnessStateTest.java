package com.agent.harness.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HarnessStateTest {

    @Test
    public void testDefaultValues() {
        HarnessState state = new HarnessState();
        assertEquals("porridge", state.getExecutionEngine());
        assertEquals("default", state.getCurrentModel());
        assertFalse(state.isPlanMode());
        assertFalse(state.isDangerouslySkipPermissions());
    }

    @Test
    public void testSetters() {
        HarnessState state = new HarnessState();
        
        state.setExecutionEngine("agy");
        assertEquals("agy", state.getExecutionEngine());
        
        state.setCurrentModel("gemini-1.5-pro");
        assertEquals("gemini-1.5-pro", state.getCurrentModel());
        
        state.setPlanMode(true);
        assertTrue(state.isPlanMode());
        
        state.setDangerouslySkipPermissions(true);
        assertTrue(state.isDangerouslySkipPermissions());
    }
}
