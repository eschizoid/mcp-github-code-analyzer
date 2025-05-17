package mcp.code.analysis.config

/** Configuration settings for the application. Retrieves values from environment variables or uses default values. */
data class AppConfig(
  val serverPort: Int,
  val githubToken: String,
  val workingDirectory: String,
  val modelApiUrl: String,
  val modelApiKey: String,
  val modelName: String,
) {
  companion object {
    /**
     * Creates an instance of [AppConfig] by retrieving values from environment variables. If an environment variable is
     * not set, a default value is used.
     *
     * @return An instance of [AppConfig] with the retrieved or default values.
     */
    fun fromEnv(): AppConfig =
      AppConfig(
        serverPort = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 3001,
        githubToken = System.getenv("GITHUB_TOKEN") ?: "",
        workingDirectory =
          System.getenv("WORKING_DIRECTORY") ?: System.getProperty("java.io.tmpdir").plus("/mcp-code-analysis"),
        modelApiUrl = System.getenv("MODEL_API_URL") ?: "http://localhost:11434/api",
        modelApiKey = System.getenv("MODEL_API_KEY") ?: "",
        modelName = System.getenv("MODEL_NAME") ?: "llama3.3",
      )
  }
}
