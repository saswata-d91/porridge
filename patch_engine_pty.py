import re

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    target = 'Process process = new ProcessBuilder("sh", "-c", cliCmd)'
    
    # We want to insert the PTY wrapper right before ProcessBuilder
    replacement = '''
                    String osName = System.getProperty("os.name").toLowerCase();
                    if (osName.contains("mac")) {
                        cliCmd = "script -q /dev/null " + cliCmd;
                    } else if (osName.contains("linux")) {
                        cliCmd = "script -q /dev/null -c \\"" + cliCmd.replace("\\"", "\\\\\\\"") + "\\"";
                    }
                    Process process = new ProcessBuilder("sh", "-c", cliCmd)'''

    if target in content:
        content = content.replace(target, replacement)
        with open(filepath, 'w') as f:
            f.write(content)
        print("Patched " + filepath)
    else:
        print("Target not found in " + filepath)

patch_file('porridge-core/src/main/java/com/agent/harness/engine/AutonomousHarnessEngine.java')
patch_file('porridge-core/src/main/java/com/agent/harness/tools/ExternalAgentOrchestratorTool.java')
