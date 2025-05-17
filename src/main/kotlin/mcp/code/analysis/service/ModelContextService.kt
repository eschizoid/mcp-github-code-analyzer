package mcp.code.analysis.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import mcp.code.analysis.config.AppConfig
import org.slf4j.LoggerFactory

@Serializable
data class OllamaRequest(
  val model: String = AppConfig().modelName,
  val prompt: String,
  val stream: Boolean = false,
  val options: OllamaOptions = OllamaOptions(),
)

@Serializable data class OllamaOptions(val temperature: Double = 0.7, val num_predict: Int = 1000)

@Serializable
data class OllamaResponse(val model: String? = null, val response: String? = null, val done: Boolean = false)

/**
 * Service for interacting with the Ollama model API. Provides methods to generate responses, summaries, and insights
 * using the model.
 */
class ModelContextService {
  private val logger = LoggerFactory.getLogger(ModelContextService::class.java)
  private val config = AppConfig()
  private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    encodeDefaults = true
  }

  private val client =
    HttpClient(CIO) {
      install(ContentNegotiation) { json(json) }
      install(HttpTimeout) {
        requestTimeoutMillis = 10.minutes.inWholeMilliseconds
        socketTimeoutMillis = 10.minutes.inWholeMilliseconds
        connectTimeoutMillis = 120_000
      }
    }

  /**
   * Generate a response from the Ollama model based on the provided prompt.
   *
   * @param prompt A well-structured request for the model to analyze
   * @return The generated response as a string
   */
  suspend fun generateResponse(prompt: String): String {
    try {
      logger.debug("Sending request to Ollama with prompt: ${prompt.take(100)}...")

      val request = OllamaRequest(model = "llama3.2", prompt = prompt)

      val ollamaApiUrl = "${config.modelApiUrl}/generate"
      val httpResponse =
        client.post(ollamaApiUrl) {
          contentType(ContentType.Application.Json)
          setBody(request)
          timeout {
            requestTimeoutMillis = 10.minutes.inWholeMilliseconds
            socketTimeoutMillis = 10.minutes.inWholeMilliseconds
            connectTimeoutMillis = 120_000
          }
        }

      if (!httpResponse.status.isSuccess()) {
        val errorBody = httpResponse.bodyAsText()
        logger.error("Ollama API error: ${httpResponse.status} - $errorBody")
        return "API error (${httpResponse.status}): $errorBody"
      }

      val response = httpResponse.body<OllamaResponse>()
      return response.response ?: "No response generated"
    } catch (e: Exception) {
      logger.error("Error generating response: ${e.message}", e)
      return "Error generating response: ${e.message}"
    }
  }

  /**
   * Generate a summary of the codebase based on the provided information.
   *
   * @param codeStructure Map representing the structure of the codebase
   * @param insights List of insights extracted from code analysis
   * @param readmeContent Content of the README file from the repository
   * @return A comprehensive summary of the codebase
   */
  suspend fun generateSummary(codeStructure: Map<String, Any>, insights: List<String>, readmeContent: String): String {
    try {

      val prompt =
        """
                You are analyzing a code repository. Based on the following information:

                README Content:
                $readmeContent

                Code Structure:
                ${codeStructure.entries.joinToString("\n") { "${it.key}: ${it.value}" }}

                Key Insights:
                ${insights.joinToString("\n")}

                Create a comprehensive summary of this codebase, including:
                1. Main purpose of the project
                2. Core architecture and components
                3. Technologies used
                4. Key functionality
                5. Potential areas for improvement

                Make the summary concise but informative for a developer trying to understand this codebase.
                """
          .trimIndent()

      return generateResponse(prompt)
    } catch (e: Exception) {
      logger.error("Error generating summary: ${e.message}", e)
      return "Error generating summary: ${e.message}"
    }
  }

  /**
   * Build a prompt for the model context based on the provided code snippets.
   *
   * @param codeSnippets List of code snippets from the repository to analyze
   * @return A structured prompt for the model
   */
  fun buildPrompt(codeSnippets: List<String>): String =
    """
        You are an expert code analyzer with deep knowledge of multiple programming languages including Java, Kotlin, Python, Go, Scala, JavaScript, TypeScript, C++, Rust, Ruby, and more. Analyze the following code repository snippets and provide insights about:

        1. The overall architecture of the application
        2. Primary programming languages used and their interactions
        3. Key components and their relationships
        4. Design patterns used
        5. Potential code quality issues or improvements
        6. Security considerations
        7. Performance considerations
        8. Language-specific best practices and conventions

        Code snippets:

        ${codeSnippets.joinToString("\n\n")}

        Provide detailed analysis with specific references to the code where possible. Address language-specific concerns and identify cross-language integration points if multiple languages are used.
        """
      .trimIndent()

  /**
   * Parse the model output to extract insights.
   *
   * @param modelOutput The raw output from the model
   * @return List of extracted insights
   */
  fun parseInsights(modelOutput: String): List<String> {
    // Split the output into separate insights
    return modelOutput.split(Regex("\\n\\s*\\d+\\.\\s+|\\n\\s*-\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
  }
}
