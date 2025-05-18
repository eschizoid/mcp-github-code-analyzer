# Model Context Protocol Code Analysis Server

A Kotlin server application that analyzes GitHub repositories using AI models through the Model Context Protocol (MCP).

## Features

- Clone and analyze GitHub repositories
- Extract code structure and relationships
- Process code using Model Context Protocol
- Generate detailed insights and summaries
- Functional architecture with immutable data classes
- Multiple server modes (stdio, SSE)

## Getting Started

### Prerequisites

- JDK 23 or higher
- Kotlin 1.9.x
- Gradle 8.0 or higher
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

- repoUrl: GitHub repository URL (e.g., https://github.com/owner/repo)

Optional parameters:

- branch: Branch to analyze (default: main)

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
