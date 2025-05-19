package mcp.code.analysis.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mcp.code.analysis.config.AppConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Serializable
data class OllamaRequest(
  val model: String,
  val prompt: String,
  val stream: Boolean = false,
  val options: OllamaOptions = OllamaOptions(),
)

@Serializable data class OllamaOptions(val temperature: Double = 0.7, val num_predict: Int = -1)

@Serializable
data class OllamaResponse(val model: String? = null, val response: String? = null, val done: Boolean = false)

/**
 * Functional service for interacting with the Ollama model API. All dependencies are explicitly injected and immutable.
 */
data class ModelContextService(
  private val config: AppConfig = AppConfig.fromEnv(),
  private val httpClient: HttpClient = defaultHttpClient(),
  private val logger: Logger = LoggerFactory.getLogger(ModelContextService::class.java),
) {
  /**
   * Generate a response from the Ollama model based on the provided prompt.
   *
   * @param prompt A well-structured request for the model to analyze
   * @return The generated response as a string
   */
  suspend fun generateResponse(prompt: String): String {
    return try {
      logger.info(
        """Sending request to Ollama with prompt:
          |${prompt}..."""
          .trimIndent()
      )
      val request = OllamaRequest(model = config.modelName, prompt = prompt)
      val ollamaApiUrl = "${config.modelApiUrl}/generate"
      val httpResponse = sendRequest(ollamaApiUrl, request)

      if (!httpResponse.status.isSuccess()) {
        val errorBody = httpResponse.bodyAsText()
        logger.error("Ollama API error: ${httpResponse.status} - $errorBody")
        "API error (${httpResponse.status}): $errorBody"
      } else {
        val response = httpResponse.body<OllamaResponse>()
        logger.info(
          """Received response from Ollama:
            |${response.response}"""
            .trimIndent()
        )
        response.response ?: "No response generated"
      }
    } catch (e: Exception) {
      logger.error("Error generating response: ${e.message}", e)
      "Error generating response: ${e.message}"
    }
  }

  /**
   * Build a prompt for the model context based on the provided README file.
   *
   * @param readme List of code snippets from the repository to analyze
   * @return A structured prompt for the model
   */
  fun buildInsightsPrompt(readme: String) =
    """
    |You are an expert codebase analyst with deep knowledge of software architecture, secure and scalable design, and programming languages including Java, Kotlin, Python, Go, Scala, JavaScript, TypeScript, C++, Rust, Ruby, and more.
    |
    |You are given the README file of a repository. Analyze it thoroughly and provide a detailed breakdown addressing the following aspects **based on the README content alone**:
    |
    |1. **Overall architecture** of the application (inferred from descriptions, diagrams, or setup instructions)
    |2. **Primary programming languages** used and how they may interact
    |3. **Key components** and their relationships or dependencies
    |4. **Design patterns** mentioned or implied
    |5. **Potential code quality issues** or areas for improvement (based on tooling, structure, or conventions described)
    |6. **Security considerations** (e.g., exposed credentials, missing practices)
    |7. **Performance considerations** (e.g., use of caching, parallelism hints)
    |8. **Language-specific best practices and conventions**
    |
    |If any of the above are not directly stated, make well-reasoned inferences and clearly label them as such.
    |
    |Format your response using sections and markdown. Provide specific references to the README text where applicable. If multiple languages are used, highlight any cross-language integration points.
    |
    |README Content:
    |~~~markdown
    |${readme.replace("```","~~~")}
    |~~~"""
      .trimIndent()

  /**
   * Build a summary prompt for the model context based on the provided code structure and snippets.
   *
   * @param codeStructure Map representing the structure of the codebase
   * @param codeSnippets List of code snippets from the repository
   * @param insights List of insights generated from the README analysis
   * @return A structured prompt for the model
   */
  fun buildSummaryPrompt(codeStructure: Map<String, Any>, codeSnippets: List<String>, insights: String): String =
    """
    |You are analyzing a software code repository. You are provided with:
    |
    |Code Snippets:
    |${codeSnippets.joinToString("\n\n")}
    |
    |Key Insights:
    |$insights
    |
    |Using this information, generate a comprehensive yet accessible summary of the codebase. Your goal is to help a new developer quickly understand the project.
    |
    |Your summary should include:
    |
    |1. **Main purpose** of the project
    |2. **Core architecture and components**
    |3. **Technologies and languages** used
    |4. **Key functionality and workflows**
    |5. **Potential areas for improvement or refactoring**
    |
    |Where helpful, include **small illustrative code snippets** from the provided examples to clarify important concepts, structures, or patterns.
    |
    |Format your response with clear section headings and concise explanations. Assume the reader is technically proficient but unfamiliar with this specific codebase."""
      .trimIndent()

  private suspend fun sendRequest(url: String, request: OllamaRequest): HttpResponse {
    return httpClient.post(url) {
      contentType(ContentType.Application.Json)
      setBody(request)
      timeout {
        requestTimeoutMillis = 10.minutes.inWholeMilliseconds
        socketTimeoutMillis = 10.minutes.inWholeMilliseconds
        connectTimeoutMillis = 120_000
      }
    }
  }

  companion object {
    /** Creates a default HTTP client with the appropriate configuration. */
    fun defaultHttpClient(): HttpClient =
      HttpClient(CIO) {
        install(ContentNegotiation) {
          json(
            Json {
              ignoreUnknownKeys = true
              coerceInputValues = true
              isLenient = true
              encodeDefaults = true
            }
          )
        }
        install(HttpTimeout) {
          requestTimeoutMillis = 10.minutes.inWholeMilliseconds
          socketTimeoutMillis = 10.minutes.inWholeMilliseconds
          connectTimeoutMillis = 120_000
        }
      }
  }
}
