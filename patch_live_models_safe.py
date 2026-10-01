import re

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # The exact block we want to replace
    # We will search for:
    # if (harnessState.getExecutionEngine().equalsIgnoreCase("agy")) {
    #     terminal.writer().println("1) default");
    # ... up to the closing brace of the switch statement block, before "} else {"
    
    start_str = 'if (harnessState.getExecutionEngine().equalsIgnoreCase("agy")) {'
    end_str = 'case "8": newModel = lineReader.readLine("\\u001B[32mEnter custom model name: \\u001B[0m").trim(); break;\n                        }'
    
    start_idx = content.find(start_str)
    end_idx = content.find(end_str)
    
    if start_idx == -1 or end_idx == -1:
        print("Could not find bounds!")
        return

    replacement = '''if (harnessState.getExecutionEngine().equalsIgnoreCase("agy")) {
                        terminal.writer().println("\\u001B[33mFetching live models from agy...\\u001B[0m");
                        terminal.writer().flush();
                        
                        java.util.List<String> liveModels = new java.util.ArrayList<>();
                        liveModels.add("default");
                        
                        try {
                            String osName = System.getProperty("os.name").toLowerCase();
                            String cmd = osName.contains("mac") ? "script -q /dev/null agy models" : "script -q /dev/null -c 'agy models'";
                            Process process = new ProcessBuilder("sh", "-c", cmd).start();
                            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
                            
                            StringBuilder outBuilder = new StringBuilder();
                            int c;
                            while ((c = reader.read()) != -1) {
                                outBuilder.append((char) c);
                            }
                            process.waitFor();
                            
                            String[] lines = outBuilder.toString().split("\\\\r?\\\\n|\\\\r");
                            for (String l : lines) {
                                l = l.replaceAll("\\\\u001B\\\\[[;\\\\d]*m", "").trim();
                                l = l.replaceAll("^.*?Fetching available models\\\\.\\\\.\\\\.", "").trim();
                                if (l.isEmpty()) continue;
                                
                                String[] chunks = l.split("\\\\s+");
                                String first = chunks[0];
                                if (first.length() > 5 && first.contains("-") && (first.startsWith("gemini") || first.startsWith("claude") || first.startsWith("gpt") || first.startsWith("auggie"))) {
                                    if (!liveModels.contains(first)) {
                                        liveModels.add(first);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            terminal.writer().println("Failed to fetch live models: " + e.getMessage());
                        }
                        
                        for (int i=0; i<liveModels.size(); i++) {
                            terminal.writer().println((i+1) + ") " + liveModels.get(i));
                        }
                        terminal.writer().println((liveModels.size() + 1) + ") Custom / Manual Entry");
                        terminal.writer().flush();
                        
                        String modelChoice = lineReader.readLine("\\u001B[32mSelect model (1-" + (liveModels.size()+1) + ") [Current: " + harnessState.getCurrentModel() + "]: \\u001B[0m");
                        int choiceIndex = -1;
                        try {
                            choiceIndex = Integer.parseInt(modelChoice.trim()) - 1;
                        } catch (NumberFormatException ignored) {}
                        
                        if (choiceIndex >= 0 && choiceIndex < liveModels.size()) {
                            newModel = liveModels.get(choiceIndex);
                        } else if (choiceIndex == liveModels.size()) {
                            newModel = lineReader.readLine("\\u001B[32mEnter custom model name: \\u001B[0m").trim();
                        }'''
    
    new_content = content[:start_idx] + replacement + content[end_idx + len(end_str):]
    with open(filepath, 'w') as f:
        f.write(new_content)
    print("Patched!")

patch_file('porridge-core/src/main/java/com/agent/harness/engine/AutonomousHarnessEngine.java')
