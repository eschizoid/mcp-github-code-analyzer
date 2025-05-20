# 🤖 MCP Code Analysis Server

A Kotlin server application that analyzes GitHub repositories using AI models through the Model Context Protocol (MCP).

## Features

- Clone and analyze GitHub repositories
- Extract code structure and relationships
- Process code using Model Context Protocol
- Generate detailed insights and summaries
- Multiple server modes (stdio, SSE)

## Getting Started

### Prerequisites

- JDK 23 or higher
- Kotlin 1.9.x
- Gradle 8.14 or higher
- [Ollama](https://github.com/ollama/ollama) 3.2 or higher (for model API)
- [MCP Inspector](https://github.com/modelcontextprotocol/inspector) (for model context protocol)

### Installation

1. Clone this repository
2. Build the project using Gradle:

  ```bash
  ./gradlew build
  ```

3. Start Ollama server:

  ```bash
  ollama run llama3.2
  ```

4. Start the MCP Inspector:

  ```bash
  npx @modelcontextprotocol/inspector
  ```

5. You can access the MCP Inspector at `http://127.0.0.1:6274/` and configure the `Arguments` to start the server:

![Connect](https://raw.githubusercontent.com/eschizoid/mcp-github-code-analyzer/main/img/mcp_inspector_connect_server.png)

Use the following arguments:

  ```bash
  ~/mcp-github-code-analyzer/build/libs/mcp-github-code-analyzer-0.1.0-SNAPSHOT.jar --stdio
  ```

6. Click `Connect` to start the MCP Server.

7. Then you can click the tab `Tools` to discover the available tools. The Tool `analyze-repository` should be listed
   and ready to be used. Click on the `analyze-repository` tool to see its details and parameters:

![Tools Tab](https://raw.githubusercontent.com/eschizoid/mcp-github-code-analyzer/main/img/mcp_inspector_tools_tab.png)

8. Finally, capture the `repoUrl` and `branch` parameters and click `Run Tool` to start the analysis:

![Run Tool](https://raw.githubusercontent.com/eschizoid/mcp-github-code-analyzer/main/img/mcp_inspector_run_tool.png)

### Configuration

The application uses environment variables for configuration:

- `SERVER_PORT`: The port for the server (default: 3001)
- `GITHUB_TOKEN`: GitHub token for API access (optional)
- `WORKING_DIRECTORY`: Directory for cloning repositories (default: system temp + "/mcp-code-analysis")
- `MODEL_API_URL`: URL for the model API (default: "http://localhost:11434/api")
- `MODEL_API_KEY`: API key for the model service (optional)
- `MODEL_NAME`: Name of the model to use (default: "llama3.2")

### Running the Application

The server supports multiple modes:

```bash
# Default: Run as SSE server with Ktor plugin on port 3001
./gradlew run

# Run with standard input/output
./gradlew run --args="--stdio"

# Run as SSE server with Ktor plugin on custom port
./gradlew run --args="--sse-server-ktor 3002"

# Run as SSE server with plain configuration
./gradlew run --args="--sse-server 3002"

# With custom environment variables:
SERVER_PORT=3002 MODEL_NAME=mistral ./gradlew run
```

## Model Context Protocol

This server implements the Model Context Protocol (MCP) and provides the following tool:

- `analyze-repository` Analyzes GitHub repositories to provide code insights and structure summary.

Required parameters:

- `repoUrl`: GitHub repository URL (e.g., https://github.com/owner/repo)

Optional parameters:

- `branch`: Branch to analyze (default: main)

## Project Structure

- `Main.kt`: Application entry point
- `config/`: Configuration classes
    - `AppConfig.kt`: Immutable configuration data class
- `server/`: MCP server implementation
    - `Server.kt`: Functional MCP server with multiple run modes
- `service/`: Core services for repository analysis
    - `GitService.kt`: Handles repository cloning
    - `CodeAnalyzer.kt`: Analyzes code structure
    - `ModelContextService.kt`: Generates insights using AI models
    - `RepositoryAnalysisService.kt`: Coordinates the analysis process

All services are implemented as functional data classes with explicit dependency injection.

## License

This project is open source and available under the MIT License.
