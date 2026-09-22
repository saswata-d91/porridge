package com.agent.harness.config;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
public class HarnessState {
    private boolean dangerouslySkipPermissions = false;
    private boolean planMode = false;
    private String currentModel = "default";
    private String executionEngine = "porridge";

    @Value("${porridge.artifacts-dir:.porridge/conversations}")
    private String artifactsDir;

    public String getArtifactsDir() {
        return artifactsDir;
    }


    public String getExecutionEngine() {
        return executionEngine;
    }

    public void setExecutionEngine(String executionEngine) {
        this.executionEngine = executionEngine;
    }

    public boolean isDangerouslySkipPermissions() {
        return dangerouslySkipPermissions;
    }

    public void setDangerouslySkipPermissions(boolean dangerouslySkipPermissions) {
        this.dangerouslySkipPermissions = dangerouslySkipPermissions;
    }

    public boolean isPlanMode() {
        return planMode;
    }

    public void setPlanMode(boolean planMode) {
        this.planMode = planMode;
    }

    public String getCurrentModel() {
        return currentModel;
    }

    public void setCurrentModel(String currentModel) {
        this.currentModel = currentModel;
    }
}
