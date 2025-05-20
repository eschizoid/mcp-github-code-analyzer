package mcp.code.analysis.config

/**
 * Immutable configuration settings for the application. Retrieves values from environment variables or uses default
 * values.
 */
data class AppConfig(
  val serverPort: Int,
  val githubToken: String,
  val cloneDirectory: String,
  val logDirectory: String,
  val modelApiUrl: String,
  val modelApiKey: String,
  val modelName: String,
) {
  companion object {

    /**
     * Creates an instance of [AppConfig] by retrieving values from environment variables. If an environment variable is
     * not set, a default value is used.
     *
     * @param getEnvFunc Function to retrieve environment variables (defaults to System.getenv)
     * @return An instance of [AppConfig] with the retrieved or default values.
     */
    fun fromEnv(getEnvFunc: (String) -> String? = System::getenv): AppConfig =
      AppConfig(
        serverPort = getEnvFunc("SERVER_PORT")?.toIntOrNull() ?: 3001,
        githubToken = getEnvFunc("GITHUB_TOKEN") ?: "",
        cloneDirectory = getEnvFunc("CLONE_DIRECTORY") ?: "/tmp/mcp-github-code-analyzer/clones",
        logDirectory = getEnvFunc("LOGS_DIRECTORY") ?: "/tmp/mcp-github-code-analyzer/logs",
        modelApiUrl = getEnvFunc("MODEL_API_URL") ?: "http://localhost:11434/api",
        modelApiKey = getEnvFunc("MODEL_API_KEY") ?: "",
        modelName = getEnvFunc("MODEL_NAME") ?: "llama3.2",
      )
  }
}
