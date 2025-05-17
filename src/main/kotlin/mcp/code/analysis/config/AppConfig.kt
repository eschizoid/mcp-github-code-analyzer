package mcp.code.analysis.config

/** Configuration settings for the application. Retrieves values from environment variables or uses default values. */
class AppConfig {
  val serverPort: Int = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 3001
  val githubToken: String = System.getenv("GITHUB_TOKEN") ?: ""
  val workingDirectory: String =
    System.getenv("WORKING_DIRECTORY") ?: System.getProperty("java.io.tmpdir").plus("/mcp-code-analysis")

  // Default to local Ollama instance
  val modelApiUrl: String = System.getenv("MODEL_API_URL") ?: "http://localhost:11434/api"

  // Not needed for Ollama local deployments
  val modelApiKey: String = System.getenv("MODEL_API_KEY") ?: ""

  val modelName: String = System.getenv("MODEL_NAME") ?: "llama3.3"
}
