package mcp.code.analysis.config

/*
 * This file contains the configuration settings for the application.
 * It retrieves values from environment variables or uses default values.
 */
class AppConfig {
  val serverPort: Int = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8080
  val githubToken: String = System.getenv("GITHUB_TOKEN") ?: ""
  val workingDirectory: String = System.getenv("WORKING_DIRECTORY") ?: "temp"

  // Default to local Ollama instance
  val modelApiUrl: String = System.getenv("MODEL_API_URL") ?: "http://localhost:11434/api"

  // Not needed for Ollama local deployments
  val modelApiKey: String = System.getenv("MODEL_API_KEY") ?: ""

  val modelName: String = System.getenv("MODEL_NAME") ?: "llama3.2"
}
