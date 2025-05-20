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
    |You are an expert codebase analyst with deep expertise in software architecture, secure and scalable system design, and programming languages including Java, Kotlin, Python, Go, Scala, JavaScript, TypeScript, C++, Rust, Ruby, and others.
    |
    |You will be provided with the README file of a repository. Based on the README **alone**, provide a comprehensive analysis covering the following aspects:
    |
    |1. **Overall architecture** — inferred from descriptions, diagrams, setup steps, or configuration details.
    |2. **Primary programming languages** — identify the main languages used and describe how they interact if applicable.
    |3. **Key components and dependencies** — identify modules, services, tools, or third-party integrations and their relationships.
    |4. **Design patterns** — mention any explicitly referenced or implicitly suggested architectural or code patterns.
    |5. **Code quality signals** — identify any potential issues or areas of improvement (e.g., based on structure, naming, tooling).
    |6. **Security considerations** — highlight any security best practices followed or missing (e.g., credential handling, auth mechanisms).
    |7. **Performance considerations** — discuss caching, concurrency, resource management, or deployment implications.
    |8. **Language-specific practices** — note idiomatic usage or violations of best practices for the identified languages.
    |
    |If any of the above are not explicitly described, provide clearly labeled **inferences** based on available information.
    |
    |Format your response in markdown using clear sections. Include direct references to specific README content where relevant. If multiple languages are involved, explain any cross-language integration points.
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
    |You are analyzing a software code repository. You are provided with the following information:
    |
    |**Code Structure:**
    |${codeStructure.entries.joinToString("\n") { "${it.key}: ${it.value}" }}
    |
    |**Code Snippets:**
    |${codeSnippets.joinToString("\n\n")}
    |
    |**Key Insights:**
    |$insights
    |
    |Using this information, write a comprehensive and accessible summary of the codebase. Your goal is to help a technically proficient developer who is new to the project quickly understand its structure and purpose.
    |
    |Your summary must cover the following aspects:
    |
    |1. **Main purpose** of the project
    |2. **Core architecture and components**
    |3. **Technologies and programming languages** used
    |4. **Key functionality and workflows**
    |5. **Potential areas for improvement or refactoring**
    |
    |Where helpful, include **brief illustrative code snippets** from the examples provided to clarify key concepts, architectural decisions, or coding patterns.
    |
    |Format your response using markdown with clear section headings and concise, informative language. Avoid speculation beyond the provided inputs unless clearly stated as inference.
    |"""
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
