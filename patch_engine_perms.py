import re

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # The exact block we want to replace
    # We will search for:
    # cliCmd = "agy --print \\"" + prompt.replace("\\"", "\\\\\\\"") + "\\"" + (!extModel.equals("default") ? " --model " + extModel : "");
    
    start_str = 'cliCmd = "agy --print \\"" + prompt.replace("\\"", "\\\\\\\"") + "\\"" + (!extModel.equals("default") ? " --model " + extModel : "");'
    
    start_idx = content.find(start_str)
    
    if start_idx == -1:
        print("Could not find bounds!")
        return

    replacement = '''cliCmd = "agy --print \\"" + prompt.replace("\\"", "\\\\\\\"") + "\\"" + (!extModel.equals("default") ? " --model " + extModel : "");
                    if (harnessState.isDangerouslySkipPermissions()) {
                        cliCmd += " --dangerously-skip-permissions";
                    }'''
    
    new_content = content[:start_idx] + replacement + content[start_idx + len(start_str):]
    with open(filepath, 'w') as f:
        f.write(new_content)
    print("Patched " + filepath)

patch_file('porridge-core/src/main/java/com/agent/harness/engine/AutonomousHarnessEngine.java')
