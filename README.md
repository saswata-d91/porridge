# Porridge: Autonomous Agent Harness

Porridge is a provider-agnostic, multi-module Java REPL harness designed to host, orchestrate, and manage AI Agents.

## 🚀 Getting Started

Porridge is self-bootstrapping and comes with native launcher scripts for Windows, macOS, and Linux. These scripts will automatically download a sandboxed JRE 17 if it isn't installed, build the Maven project, and boot the REPL.

**On Mac/Linux:**
```bash
./porridge.sh
```

**On Windows:**
```bat
.\porridge.bat
```

## 🚀 Native Model Support
Because Porridge is built on top of **Spring AI**, it natively supports running LLM models hosted across a vast array of providers without changing the core engine code. You can plug in API keys and configure models for:
- **Google Vertex AI** (Gemini)
- **AWS Bedrock** (Anthropic Claude, Amazon Titan, Meta Llama)
- **OpenAI** and **Azure OpenAI**
- **Ollama** (for local, offline inference)
- **Mistral**, **HuggingFace**, and more.

## 🤖 External Engine Orchestration
No API keys? No problem. Porridge features a powerful **Engine Bypass** system. You can instruct Porridge to act as a frontend wrapper that delegates execution to external AI CLIs installed on your host OS.
Supported external engines include:
- `agy` (Google Antigravity)
- `claude` or `claude-code` (Anthropic)
- `codex`
- `auggie`
- `gh` (GitHub Copilot CLI)

### Interactive Menus
Porridge includes interactive menus for configuring your execution environment:
* `/engine`: Select between the native Spring AI engine or an external CLI.
* `/model`: Select a model. The menu dynamically adjusts to show models supported by your current engine (e.g., Antigravity specific models).

Simply type `/engine agy` in the REPL, and Porridge will securely pipe prompts to the external CLI while retaining full context history and sandbox virtualization.


## 🖼️ Multimodality & File Management

Porridge treats files and binary data as first-class citizens:

### 1. Native Image, Audio, and Video Parsing
You can stream binary media directly into the LLM context window simply by referencing their file paths in your prompt! Porridge acts like Claude Code and will automatically detect any valid file paths (absolute or relative) typed into the REPL. If the file is an image, audio, or video, and your model supports it (like Gemini 1.5 Pro), Porridge automatically bundles the binary into a native Spring AI `Media` payload.
**Usage:** `Summarize this meeting: path/to/recording.mp4`
**Supported Formats:** `.png`, `.jpg`, `.mp4`, `.mp3`, `.wav`, etc.

### 2. Deep Document Parsing (Apache Tika)
Agents have access to a native `ReadDocumentTool` powered by embedded **Apache Tika**. If you ask Porridge to read a PDF, Excel Spreadsheet (`.xlsx`), Word Document (`.docx`), or PowerPoint, it will automatically parse the binary and extract the readable text for the context window.

### 3. Conversation-Isolated Artifacts
When drafting complex plans or generating reports, Porridge automatically silos generated artifacts into isolated directories grouped by the active conversation session (e.g., `.porridge/conversations/<sessionId>`). This keeps your root project clean across different agent threads.
*Configure this via `application.properties`:* `porridge.artifacts-dir=.porridge/conversations`

## 🧩 Extensibility: Skills, Tools, and MCP Servers

Porridge is designed to be deeply extensible. It recursively scans your directory tree to dynamically load knowledge and capabilities.

### 1. Adding Context and Skills
To provide the agent with local knowledge or specialized instruction sets:
* **Global Instructions**: Place a `PORRIDGE.md` file in your project root.
* **Specialized Skills**: Create a directory named `.porridge/skills/` and place any markdown (`.md`) files inside it. Porridge will parse these files and dynamically mount them into the agent's system prompt context.

### 2. Adding Dynamic Executable Tools
To give the agent custom tools to execute:
* Create a directory named `.porridge/tools/`.
* Place executable scripts (e.g., `deploy.sh`, `format.py`) inside.
* **Important**: You must ensure the files have the executable bit set (e.g., `chmod +x deploy.sh`). Porridge will automatically discover them and expose them to the LLM.

### ⚙️ Configuration Properties
You can override the default locations for instructions, skills, and tools in your `application.properties` (or `application.yml`):
```properties
porridge.instruction-files=PORRIDGE.md,INSTRUCTIONS.md
porridge.skills-dir=.porridge/skills
porridge.tools-dir=.porridge/tools
```

### 3. Configuring MCP (Model Context Protocol) Servers
Porridge natively supports the Model Context Protocol (MCP). It actively hunts for MCP configurations across your system and mounts the resulting tools to the Spring AI context.
Porridge looks for standard MCP JSON configuration files (like the one used by Claude Desktop) in the following locations:
* `.porridge/mcp.json` (Local repository configuration)
* `.claude.json`
* Your global `agy` config (`~/.gemini/antigravity-cli/mcp.json`)
* Your global Claude Desktop configs

**Example `.porridge/mcp.json` structure:**
```json
{
  "mcpServers": {
    "weather-server": {
      "command": "node",
      "args": ["/path/to/weather-mcp/build/index.js"],
      "env": {}
    }
  }
}
```

### 🤝 Hosting Porridge as an MCP Server
You can expose the entire Porridge environment (including its sandbox manager, tools, and Semantic Graph Memory) to *other* tools (like Claude Desktop, Cursor, or Antigravity) by running Porridge in MCP Server Mode.
To do this, launch the JAR with the `--mcp-server` flag:
```bash
java -jar porridge-core/target/claude-code-java-harness-0.0.1-SNAPSHOT.jar --mcp-server
```
This skips the REPL and binds a standard MCP JSON-RPC listener over `stdio`. It exposes the `invoke_porridge_agent` tool, allowing external systems to safely spin up sandboxed subagents within your workspace!

## 🎮 Interactive REPL Commands
Porridge is bundled with several powerful JLine3 slash commands to control the harness environment interactively:

* `/wizard`: Launches an interactive configuration wizard to scaffold new Skills (Markdown) or configure new MCP Tool Servers without leaving the REPL.
* `/engine [name]`: Switches the execution engine (e.g., `porridge`, `agy`, `claude-code`). If run without arguments, it opens an interactive selection menu.
* `/model [name]`: Switches the current LLM. If run without arguments, it opens a dynamic menu based on your current engine.
* `/plan`: Toggles Planning Mode, instructing the agent to draft a plan for your approval before execution.
* `/dangerously-skip-permissions`: Disables internal boundary checks, giving the agent raw, unverified write access to your environment.
* `/learn <fact>`: Manually injects a rule or fact into the Semantic Graph Memory.
* `/unlearn <fact>`: Manually removes a rule or fact from the Semantic Graph Memory.
* `/summarize [instructions]`: Compresses the current chat context window into a system summary to save tokens.

## 🧠 Semantic Graph Memory
Porridge features a native, local JSON-backed **Semantic Graph Memory Database**. 
- Agents can autonomously call `addMemoryTool` and `deleteMemoryTool` to learn and unlearn rules.
- Users can manually use the `/learn <fact>` and `/unlearn <fact>` slash commands.
- The DB semantically matches required facts during execution, keeping the LLM context window clean.

## 🛠 Multi-Module Architecture
The repository is split into two modules:
1. `porridge-core`: The main JLine3 REPL loop, Subagent sandbox manager, and orchestrator.
2. `porridge-graph-memory`: An isolated Graph Memory module that can be exported to other Spring AI projects.

## 🔒 Virtualization and Sandboxing
Subagents invoked by Porridge are strictly sandboxed using Git worktrees. Operations are isolated to `.porridge/sandboxes/subagent-<id>`, ensuring that experimental agent loops do not corrupt your main project files.