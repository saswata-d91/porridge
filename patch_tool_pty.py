import re

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    target = 'Process process = new ProcessBuilder("sh", "-c", cmd)'
    
    replacement = '''
                String osName = System.getProperty("os.name").toLowerCase();
                if (osName.contains("mac")) {
                    cmd = "script -q /dev/null " + cmd;
                } else if (osName.contains("linux")) {
                    cmd = "script -q /dev/null -c \\"" + cmd.replace("\\"", "\\\\\\\"") + "\\"";
                }
                Process process = new ProcessBuilder("sh", "-c", cmd)'''

    if target in content:
        content = content.replace(target, replacement)
        with open(filepath, 'w') as f:
            f.write(content)
        print("Patched " + filepath)
    else:
        print("Target not found in " + filepath)

patch_file('porridge-core/src/main/java/com/agent/harness/tools/ExternalAgentOrchestratorTool.java')
