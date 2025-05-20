package mcp.code.analysis.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AppConfigTest {

  @Test
  fun `fromEnv uses default values when environment variables are not set`() {
    // Acts
    val config = AppConfig.fromEnv { null }

    // Assert
    assertEquals(3001, config.serverPort)
    assertEquals("", config.githubToken)
    assertEquals("/tmp/mcp-github-code-analyzer/clones", config.cloneDirectory)
    assertEquals("/tmp/mcp-github-code-analyzer/logs", config.logDirectory)
    assertEquals("http://localhost:11434/api", config.modelApiUrl)
    assertEquals("", config.modelApiKey)
    assertEquals("llama3.2", config.modelName)
  }

  @Test
  fun `fromEnv uses environment variables when available`() {
    // Arrange
    val envVars =
      mapOf(
        "SERVER_PORT" to "8080",
        "GITHUB_TOKEN" to "test-token",
        "CLONE_DIRECTORY" to "/custom/clone/dir",
        "LOGS_DIRECTORY" to "/custom/logs/dir",
        "MODEL_API_URL" to "https://api.example.com",
        "MODEL_API_KEY" to "test-api-key",
        "MODEL_NAME" to "custom-model",
      )

    // Act
    val config = AppConfig.fromEnv { key -> envVars[key] }

    // Assert
    assertEquals(8080, config.serverPort)
    assertEquals("test-token", config.githubToken)
    assertEquals("/custom/clone/dir", config.cloneDirectory)
    assertEquals("/custom/logs/dir", config.logDirectory)
    assertEquals("https://api.example.com", config.modelApiUrl)
    assertEquals("test-api-key", config.modelApiKey)
    assertEquals("custom-model", config.modelName)
  }

  @Test
  fun `fromEnv handles invalid integer values`() {
    // Act
    val config = AppConfig.fromEnv { key -> if (key == "SERVER_PORT") "not-a-number" else null }

    // Assert
    assertEquals(3001, config.serverPort, "Should use default port when env var is not a valid number")
  }
}
