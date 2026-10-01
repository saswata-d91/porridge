import re

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # The block we want to replace
    # We will locate `if (harnessState.getExecutionEngine().equalsIgnoreCase("agy")) {`
    # up to `case "6": newModel = "gpt-oss"; break;` (and its surrounding)
    
    # Let's find the start and end indices using regex
    pattern = re.compile(r'if \(harnessState\.getExecutionEngine\(\)\.equalsIgnoreCase\("agy"\)\) \{.*?terminal\.writer\(\)\.println\("8\) Custom / Manual Entry"\);.*?terminal\.writer\(\)\.flush\(\);.*?String modelChoice = lineReader\.readLine.*?switch \(modelChoice\.trim\(\)\) \{.*?(case "[1-8]":[^}]+)+.*?\}', re.DOTALL)
    
    match = pattern.search(content)
    if not match:
        print("Pattern not found!")
        return
        
    replacement = '''if (harnessState.getExecutionEngine().equalsIgnoreCase("agy")) {
                        terminal.writer().println("Fetching live models from agy...");
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
                        }
                    }'''
                    
    content = content[:match.start()] + replacement + content[match.end():]
    
    with open(filepath, 'w') as f:
        f.write(content)
    print("Patched " + filepath)

patch_file('porridge-core/src/main/java/com/agent/harness/engine/AutonomousHarnessEngine.java')
