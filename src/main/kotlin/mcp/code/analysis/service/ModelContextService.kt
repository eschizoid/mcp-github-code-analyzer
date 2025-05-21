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

@Serializable data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(
  val model: String,
  val messages: List<ChatMessage>,
  val stream: Boolean = false,
  val max_tokens: Int = 8192,
)

@Serializable data class ChatResponse(val message: ChatMessage? = null)

/** Service for interacting with the Ollama model API. All dependencies are explicitly injected and immutable. */
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
        """|Sending chat request to Ollama with prompt:
           |
           |$prompt..."""
          .trimMargin()
      )
      val request =
        ChatRequest(
          model = config.modelName,
          messages =
            listOf(
              ChatMessage(role = "system", content = "You are a helpful assistant who explains software codebases."),
              ChatMessage(role = "user", content = prompt),
            ),
          max_tokens = 8192,
          stream = false,
        )

      val ollamaApiUrl = "${config.modelApiUrl}/chat"
      val httpResponse = sendRequest(ollamaApiUrl, request)

      if (!httpResponse.status.isSuccess()) {
        val errorBody = httpResponse.bodyAsText()
        logger.error("Ollama API error: ${httpResponse.status} - $errorBody")
        "API error (${httpResponse.status}): $errorBody"
      } else {
        val response = httpResponse.body<ChatResponse>()
        val reply = response.message?.content?.trim() ?: "No reply received"
        logger.info(
          """|Received reply from Ollama:
             |
             |${reply}"""
            .trimMargin()
        )
        reply
      }
    } catch (e: Exception) {
      logger.error("Error generating response: ${e.message}", e)
      "Error generating response: ${e.message}"
    }
  }

  /**
   * Build a prompt for the model context based on the provided README file.
   *
   * @param codeSnippets List of code snippets from the repository to analyze
   * @param readme Content of the README file
   * @return A structured prompt for the model
   */
  fun buildInsightsPrompt(codeSnippets: List<String>, readme: String) =
    """|You are analyzing a software codebase that includes a README file and source code files. Your task is to extract a structured, file-by-file analysis of the codebase's architecture, components, and interconnections.
       |
       |Use only the information provided below.
       |
       |----------------------
       |README Content:
       |
       |~~~markdown
       |${readme.replace("```", "~~~")}
       |~~~
       |
       |----------------------
       |Code Snippets:
       |
       |${codeSnippets.joinToString("\n\n")}
       |----------------------
       |
       |For each file, extract the following:
       |- File path and programming language
       |- Main classes, functions, or data structures defined
       |- Purpose of the file (what it does and why it exists)
       |- Key public interfaces (classes, methods, functions)
       |- Dependencies and relationships (e.g., imports, API usage, method calls across files)
       |
       |Output using this format:
       |
       |### File: path/to/file.ext (Language: X)
       |- **Purpose**: ...
       |- **Key Components**:
       |  - ...
       |- **Relationships**:
       |  - ...
       |
       |Important:
       |- Be specific and factual; avoid speculation.
       |- Use information from comments, function names, or obvious patterns.
       |- Summarize concisely; no fluff.
       |"""
      .trimMargin()

  /**
   * Build a summary prompt for the model context based on the provided code structure and snippets.
   *
   * @param insights From the first Prompt step
   * @return A structured prompt for the model
   */
  fun buildSummaryPrompt(insights: String): String =
    """|You are writing a high-level, technically accurate summary of a software codebase. The reader is a developer unfamiliar with the project, so your explanation should clarify the purpose, structure, and important technical details.
       |
       |Use the structured analysis below to guide your summary.
       |
       |----------------------
       |Structural Analysis:
       |
       |${parseInsights(insights)}
       |----------------------
       |
       |Include the following in your output:
       |
       |1. **Main Purpose** – What the software does, the problem it solves, and who uses it.
       |2. **Architecture Overview** – Key components or layers and how they interact. Describe patterns if evident (e.g., microservices, event-driven, layered architecture).
       |3. **Technologies and Languages** – List the programming language(s), major frameworks/libraries, and any notable tools.
       |4. **Key Workflows** – Show how data or control flows through the system. Prefer concrete examples (e.g., "Kafka -> Consumer -> Metrics tracker -> Monitoring system").
       |5. **Strengths and Weaknesses** – Mention any strengths (modularity, patterns, scalability) and issues (tight coupling, complexity, missing docs).
       |
       |Also consider:
       |- Noteworthy classes, functions, or modules
       |- Signs of testability, extensibility, or maintainability
       |- Use of design patterns (Builder, Observer, etc.)
       |
       |Write the output in well-structured **Markdown**. Be concise, insightful, and avoid guessing.
       |"""
      .trimMargin()

  private suspend fun sendRequest(url: String, request: ChatRequest): HttpResponse {
    return httpClient.post(url) {
      contentType(ContentType.Application.Json)
      setBody(request)
      timeout {
        requestTimeoutMillis = 60.minutes.inWholeMilliseconds
        socketTimeoutMillis = 60.minutes.inWholeMilliseconds
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
          requestTimeoutMillis = 60.minutes.inWholeMilliseconds
          socketTimeoutMillis = 60.minutes.inWholeMilliseconds
          connectTimeoutMillis = 120_000
        }
      }

    fun parseInsights(insights: String): String {
      val lines = insights.lines()
      val fileIndex = lines.indexOfFirst { it.trimStart().startsWith("### File:") }
      return if (fileIndex != -1) lines.drop(fileIndex).joinToString("\n") else insights
    }
  }
}
