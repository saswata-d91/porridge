import re

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # The exact block we want to replace
    # cmd = "agy --print \\"" + req.prompt().replace("\\"", "\\\\\\\"") + "\\"";
    
    start_str = 'cmd = "agy --print \\"" + req.prompt().replace("\\"", "\\\\\\\"") + "\\"";'
    
    start_idx = content.find(start_str)
    
    if start_idx == -1:
        print("Could not find bounds!")
        return

    replacement = 'cmd = "agy --print \\"" + req.prompt().replace("\\"", "\\\\\\\"") + "\\" --dangerously-skip-permissions";'
    
    new_content = content[:start_idx] + replacement + content[start_idx + len(start_str):]
    with open(filepath, 'w') as f:
        f.write(new_content)
    print("Patched " + filepath)

patch_file('porridge-core/src/main/java/com/agent/harness/tools/ExternalAgentOrchestratorTool.java')
