# Model Context Protocol Server

A Kotlin server application that analyzes GitHub repositories to understand code structure and provide insights using AI
models.

## Features

- Clone and analyze GitHub repositories
- Extract code structure and relationships
- Process code using context-aware models
- Generate detailed insights and summaries
- RESTful API for repository analysis

## Getting Started

### Prerequisites

- JDK 23 or higher
- Kotlin 1.9.x
- An API key for the model service (optional)

### Installation

1. Clone this repository
2. Build the project using Gradle:
   ```
   ./gradlew build
   ```

### Configuration

The application uses environment variables for configuration:

- `SERVER_PORT`: The port for the server (default: 8080)
- `GITHUB_TOKEN`: GitHub token for API access (optional)
- `WORKING_DIRECTORY`: Directory for cloning repositories (default: "temp")
- `MODEL_API_URL`: URL for the model API (default: "http://localhost:11434/v1")
- `MODEL_API_KEY`: API key for the model service (optional)

### Running the Application

```
./gradlew run
```

Or with custom configuration:

```
SERVER_PORT=9090 MODEL_API_URL=https://your-model-api.com/v1 ./gradlew run
```

## API Usage

### Analyze a Repository

```
POST /analyze
```

Request body:

```json
{
  "repoUrl": "https://github.com/username/repository",
  "branch": "main",
  "analysisType": "full"
}
```

Analysis types:

- `full`: Analyze all code files
- `core`: Focus on core components only
- `basic`: Analyze only key files

Response:

```json
{
  "id": "12345-uuid",
  "status": "pending"
}
```

### Check Analysis Status

```
GET /status/{id}
```

Response:

```json
{
  "id": "12345-uuid",
  "status": "completed",
  "summary": "This repository is a Kotlin web application...",
  "codeStructure": {
    /* Structure details */
  },
  "insights": [
    "The application uses MVC architecture",
    "Authentication is implemented using JWT"
    /* More insights */
  ]
}
```

## Project Structure

- `src/main/kotlin/Main.kt`: Application entry point
- `config/`: Configuration classes
- `routes/`: API route definitions
- `service/`: Core services for repository analysis
    - `GitService`: Handles repository cloning
    - `CodeAnalyzer`: Analyzes code structure
    - `ModelContextService`: Generates insights using AI models

## License

This project is open source and available under the MIT License.
