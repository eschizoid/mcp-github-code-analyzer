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
  val model: String,
  val prompt: String,
  val stream: Boolean = false,
  val options: OllamaOptions = OllamaOptions(),
)

@Serializable data class OllamaOptions(val temperature: Double = 0.7, val num_predict: Int = 1000)

@Serializable
data class OllamaResponse(val model: String? = null, val response: String? = null, val done: Boolean = false)

object ModelContextService {
  private val logger = LoggerFactory.getLogger(ModelContextService::class.java)
  private val config = AppConfig.fromEnv()
  private val defaultJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    encodeDefaults = true
  }

  fun createHttpClient(json: Json = defaultJson): HttpClient =
    HttpClient(CIO) {
      install(ContentNegotiation) { json(json) }
      install(HttpTimeout) {
        requestTimeoutMillis = 60.minutes.inWholeMilliseconds
        socketTimeoutMillis = 60.minutes.inWholeMilliseconds
        connectTimeoutMillis = 120_000
      }
    }

  suspend fun generateResponse(prompt: String, client: HttpClient = createHttpClient()): String {
    return try {
      logger.debug("Sending request to Ollama with prompt: ${prompt.take(100)}...")
      val request = OllamaRequest(model = config.modelName, prompt = prompt)
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
        "API error (${httpResponse.status}): $errorBody"
      } else {
        val response = httpResponse.body<OllamaResponse>()
        response.response ?: "No response generated"
      }
    } catch (e: Exception) {
      logger.error("Error generating response: ${e.message}", e)
      "Error generating response: ${e.message}"
    }
  }

  suspend fun generateSummary(
    codeStructure: Map<String, Any>,
    insights: List<String>,
    readmeContent: String,
    client: HttpClient = createHttpClient(),
  ): String {
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
    return generateResponse(prompt, client)
  }

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

  fun parseInsights(modelOutput: String): List<String> =
    modelOutput.split(Regex("\\n\\s*\\d+\\.\\s+|\\n\\s*-\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
}
