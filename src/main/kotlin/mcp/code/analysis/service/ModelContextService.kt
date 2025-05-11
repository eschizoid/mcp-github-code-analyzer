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
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import mcp.code.analysis.config.AppConfig
import org.slf4j.LoggerFactory

@Serializable
data class OllamaRequest(
  val model: String = "llama3.2",
  val prompt: String,
  val stream: Boolean = false,
  val options: OllamaOptions = OllamaOptions(),
)

@Serializable data class OllamaOptions(val temperature: Double = 0.7, val num_predict: Int = 1000)

@Serializable
data class OllamaResponse(
  val model: String? = null,
  val response: String? = null,
  val done: Boolean = false,
)

/*
 * Service for interacting with the Ollama model API.
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

  suspend fun generateSummary(
    repoDir: File,
    codeStructure: Map<String, Any>,
    insights: List<String>,
  ): String {
    try {
      val readmeFile = findReadmeFile(repoDir)
      val readmeContent = readmeFile?.readText() ?: "No README found."

      val prompt =
        """
                You are analyzing a code repository. Based on the following information:

                README Content:
                $readmeContent

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

  private fun findReadmeFile(repoDir: File): File? {
    val readmeNames = listOf("README.md", "Readme.md", "readme.md", "README.txt", "readme.txt")
    for (name in readmeNames) {
      val file = File(repoDir, name)
      if (file.exists()) {
        return file
      }
    }
    return null
  }

  fun collectAllCodeSnippets(repoDir: File): List<String> {
    val codeFiles = findCodeFiles(repoDir)
    return codeFiles.map { file ->
      val relativePath = file.absolutePath.substring(repoDir.absolutePath.length + 1)
      "File: $relativePath\n```${getLanguageFromExtension(file.extension)}\n${file.readText()}\n```"
    }
  }

  fun collectCoreCodeSnippets(repoDir: File): List<String> {
    // Find important files like .main classes, config files, etc.
    val coreFiles =
      findCodeFiles(repoDir).filter { file ->
        val path = file.absolutePath.lowercase()
        path.contains(".main") ||
          path.contains("app") ||
          path.contains("config") ||
          file.name.contains("Application") ||
          file.name.startsWith("Main") ||
          file.name == "build.gradle" ||
          file.name == "build.gradle.kts" ||
          file.name == "pom.xml" ||
          file.name.endsWith("Config.kt") ||
          file.name.endsWith("Service.kt")
      }

    return coreFiles.map { file ->
      val relativePath = file.absolutePath.substring(repoDir.absolutePath.length + 1)
      "File: $relativePath\n```${getLanguageFromExtension(file.extension)}\n${file.readText()}\n```"
    }
  }

  fun collectImportantFiles(repoDir: File): List<String> {
    // Get a few key files
    val importantFiles = mutableListOf<File>()

    // Add build files
    val buildFiles =
      listOf(
          File(repoDir, "build.gradle"),
          File(repoDir, "build.gradle.kts"),
          File(repoDir, "pom.xml"),
        )
        .filter { it.exists() }
    importantFiles.addAll(buildFiles)

    // Find .main a file if it exists
    val possibleMainFiles =
      findCodeFiles(repoDir).filter { file ->
        file.name.equals("mcp/code/analysis/Main.kt", ignoreCase = true) ||
          file.name.startsWith("Application")
      }
    importantFiles.addAll(possibleMainFiles.take(2))

    // Add a few more interesting files
    val otherFiles =
      findCodeFiles(repoDir)
        .filter { it !in importantFiles }
        .sortedByDescending { it.length() }
        .take(3)
    importantFiles.addAll(otherFiles)

    return importantFiles.map { file ->
      val relativePath = file.absolutePath.substring(repoDir.absolutePath.length + 1)
      "File: $relativePath\n```${getLanguageFromExtension(file.extension)}\n${file.readText()}\n```"
    }
  }

  private fun findCodeFiles(dir: File): List<File> {
    val result = mutableListOf<File>()
    if (dir.isHidden || dir.name == ".git") {
      return result
    }

    val files = dir.listFiles() ?: return result

    for (file in files) {
      if (file.isDirectory) {
        result.addAll(findCodeFiles(file))
      } else if (isCodeFile(file)) {
        result.add(file)
      }
    }

    return result
  }

  private fun isCodeFile(file: File): Boolean {
    val codeExtensions =
      setOf(
        // JVM languages
        "kt",
        "java",
        "scala",
        "groovy",
        "clj",

        // Script languages
        "py",
        "rb",
        "js",
        "jsx",
        "ts",
        "tsx",
        "php",
        "pl",
        "pm",
        "sh",
        "bash",
        "ps1",

        // Systems programming
        "c",
        "cpp",
        "cc",
        "cxx",
        "h",
        "hpp",
        "cs",
        "go",
        "rs",
        "swift",
        "m",

        // Web
        "html",
        "htm",
        "css",
        "scss",
        "sass",
        "less",
        "vue",
        "svelte",

        // Data formats & Config
        "json",
        "xml",
        "yaml",
        "yml",
        "toml",
        "properties",
        "ini",
        "conf",
        "md",

        // Build files
        "gradle",
        "gradle.kts",
        "sbt",
        "pom",
        "cmake",
        "make",
        "mk",

        // Other languages
        "sql",
        "r",
        "dart",
        "lua",
        "ex",
        "exs",
        "erl",
        "hrl",
        "hs",
        "fs",
        "fsx",
        "jl",
      )
    return file.extension.lowercase() in codeExtensions
  }

  private fun getLanguageFromExtension(extension: String): String =
    when (extension.lowercase()) {
      // JVM languages
      "kt" -> "kotlin"
      "java" -> "java"
      "scala" -> "scala"
      "groovy" -> "groovy"
      "clj" -> "clojure"

      // Script languages
      "py" -> "python"
      "rb" -> "ruby"
      "js" -> "javascript"
      "jsx" -> "javascript"
      "ts" -> "typescript"
      "tsx" -> "typescript"
      "php" -> "php"
      "pl",
      "pm" -> "perl"
      "sh",
      "bash" -> "shell"
      "ps1" -> "powershell"

      // Systems programming
      "c" -> "c"
      "cpp",
      "cc",
      "cxx" -> "cpp"
      "h",
      "hpp" -> "cpp-header"
      "cs" -> "csharp"
      "go" -> "go"
      "rs" -> "rust"
      "swift" -> "swift"
      "m" -> "objective-c"

      // Web
      "html",
      "htm" -> "html"
      "css" -> "css"
      "scss",
      "sass" -> "sass"
      "less" -> "less"
      "vue" -> "vue"
      "svelte" -> "svelte"

      // Data formats
      "json" -> "json"
      "xml" -> "xml"
      "yaml",
      "yml" -> "yaml"
      "toml" -> "toml"
      "md" -> "markdown"
      "csv" -> "csv"

      // Configuration
      "gradle" -> "gradle"
      "gradle.kts" -> "gradle-kotlin"
      "properties" -> "properties"
      "ini" -> "ini"
      "conf" -> "conf"

      // Other
      "sql" -> "sql"
      "r" -> "r"
      "dart" -> "dart"
      "lua" -> "lua"
      "ex",
      "exs" -> "elixir"
      "erl",
      "hrl" -> "erlang"
      "hs" -> "haskell"
      "fs",
      "fsx" -> "fsharp"
      "jl" -> "julia"

      else -> "text"
    }

  fun buildPrompt(codeSnippets: List<String>, analysisType: String): String =
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

  fun parseInsights(modelOutput: String): List<String> {
    // Split the output into separate insights
    return modelOutput
      .split(Regex("\\n\\s*\\d+\\.\\s+|\\n\\s*-\\s+"))
      .map { it.trim() }
      .filter { it.isNotEmpty() }
  }
}
