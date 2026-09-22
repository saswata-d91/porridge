package com.agent.harness.tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ExternalAgentOrchestratorToolTest {

    @Test
    public void testCommandGeneration() throws Exception {
        ExternalAgentOrchestratorTool tool = new ExternalAgentOrchestratorTool();
        
        // This is a basic test of the logic without triggering an actual ProcessBuilder block,
        // but since the method runs the process inline, we might have to mock it or just test invalid inputs.
        
        ExternalAgentOrchestratorTool.OrchestrateRequest req = new ExternalAgentOrchestratorTool.OrchestrateRequest(
            "unknown-agent",
            "hello",
            null
        );
        
        String result = tool.orchestrateAgentTool().apply(req);
        assertTrue(result.contains("Error: Unknown agent type"), "Should reject unknown agent types");
    }
}
