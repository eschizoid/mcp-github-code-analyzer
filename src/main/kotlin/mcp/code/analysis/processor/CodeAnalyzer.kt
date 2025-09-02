package mcp.code.analysis.processor

import java.io.File
import kotlin.text.lines
import mcp.code.analysis.service.ModelContextService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

data class LanguagePatterns(
  val definitionPattern: Regex,
  val commentPrefixes: List<String>,
  val blockCommentStart: String,
  val blockCommentEnd: String,
)

data class State(val lines: List<String> = emptyList(), val inCommentBlock: Boolean = false)

/**
 * Responsible for analyzing the structure of a codebase. Identifies files, directories, and their respective metadata
 * such as size, language, imports, and declarations.
 */
data class CodeAnalyzer(
  private val binaryFileSizeThreshold: Long = 1024 * 1024, // 1MB
  private val binaryDetectionThreshold: Double = 0.05,
  private val logger: Logger = LoggerFactory.getLogger(ModelContextService::class.java),
) {

  /**
   * Collects summarized code snippets from the repository.
   *
   * @param repoDir The root directory of the repository
   * @param maxLines The maximum number of lines to include per file summary
   * @return List of code summaries with metadata
   */
  fun collectSummarizedCodeSnippets(repoDir: File, maxLines: Int = 100): List<String> =
    findCodeFiles(repoDir)
      .filter { file ->
        file.extension.lowercase() in setOf("kt", "java", "scala", "py", "rb", "js", "ts", "go", "c", "cpp", "rs") &&
          !file.absolutePath.contains("test", ignoreCase = true)
      }
      .map { file ->
        val relativePath = file.absolutePath.removePrefix(repoDir.absolutePath).removePrefix("/")
        val lang = getLanguageFromExtension(file.extension)
        val content = file.readText()
        summarizeCodeContent(relativePath, lang, content, maxLines)
      }
      .also { snippets ->
        logger.info("Collected ${snippets.size} summarized snippets from ${repoDir.absolutePath}")
        logger.debug(
          """|Snippets Found:
             |${snippets.joinToString("\n")}
             |"""
            .trimMargin()
        )
      }

  /**
   * Summarizes the content of a file.
   *
   * @param path The path of the file
   * @param language The language of the file
   * @param content The content of the file
   * @param maxLines The maximum number of lines to include in the summary
   */
  fun summarizeCodeContent(path: String, language: String, content: String, maxLines: Int = 250): String {

    val patterns =
      when (language.lowercase()) {
        "kotlin" ->
          LanguagePatterns(
            Regex(
              """(class|interface|object|enum class|data class|sealed class|fun|val|var|const|typealias|annotation class).*"""
            ),
            listOf("//"),
            "/*",
            "*/",
          )

        "scala" ->
          LanguagePatterns(
            Regex(
              """(class|object|trait|case class|case object|def|val|var|lazy val|type|implicit|sealed|abstract|override|package object).*"""
            ),
            listOf("//"),
            "/*",
            "*/",
          )

        "java" ->
          LanguagePatterns(
            Regex(
              """(class|interface|enum|@interface|record|public|private|protected|static|abstract|final|synchronized|volatile|native|transient|strictfp).*"""
            ),
            listOf("//"),
            "/*",
            "*/",
          )

        "python" ->
          LanguagePatterns(Regex("""(def|class|async def|@|import|from).*"""), listOf("#"), "\"\"\"", "\"\"\"")

        "ruby" ->
          LanguagePatterns(
            Regex("""(def|class|module|attr_|require|include|extend).*"""),
            listOf("#"),
            "=begin",
            "=end",
          )

        "javascript",
        "typescript" ->
          LanguagePatterns(
            Regex("""(function|class|const|let|var|import|export|interface|type|enum|namespace).*"""),
            listOf("//"),
            "/*",
            "*/",
          )

        "go" ->
          LanguagePatterns(
            Regex("""(func|type|struct|interface|package|import|var|const).*"""),
            listOf("//"),
            "/*",
            "*/",
          )

        "rust" ->
          LanguagePatterns(
            Regex("""(fn|struct|enum|trait|impl|pub|use|mod|const|static|type|async|unsafe).*"""),
            listOf("//"),
            "/*",
            "*/",
          )

        "c",
        "cpp" ->
          LanguagePatterns(
            Regex("""(class|struct|enum|typedef|namespace|template|void|int|char|bool|auto|extern|static|virtual).*"""),
            listOf("//"),
            "/*",
            "*/",
          )

        // Default fallback for other languages
        else ->
          LanguagePatterns(
            Regex("""(class|interface|object|enum|fun|def|function|public|private|protected|static).*"""),
            listOf("//", "#"),
            "/*",
            "*/",
          )
      }

    val definitionPattern = patterns.definitionPattern
    val commentPrefixes = patterns.commentPrefixes
    val blockCommentStart = patterns.blockCommentStart
    val blockCommentEnd = patterns.blockCommentEnd

    val isDefinition: (String) -> Boolean = { line -> line.trim().matches(definitionPattern) }

    val isCommentLine: (String) -> Boolean = { line ->
      val trimmed = line.trim()
      commentPrefixes.any { trimmed.startsWith(it) } || trimmed.startsWith(blockCommentStart) || trimmed.startsWith("*")
    }

    val processDefinitionLine: (String) -> String = { line ->
      val trimmed = line.trim()
      if (trimmed.contains("{") && !trimmed.contains("}")) "$trimmed }" else trimmed
    }

    val finalState =
      content.lines().fold(State()) { state, line ->
        if (state.lines.size >= maxLines) return@fold state
        val trimmed = line.trim()
        val nextInCommentBlock =
          when {
            trimmed.startsWith(blockCommentStart) -> true
            trimmed.endsWith(blockCommentEnd) -> false
            language.lowercase() == "python" && trimmed == "\"\"\"" -> !state.inCommentBlock
            else -> state.inCommentBlock
          }

        val shouldIncludeLine = isDefinition(line) || isCommentLine(line) || state.inCommentBlock
        val updatedLines =
          if (shouldIncludeLine)
            if (isDefinition(line)) state.lines + processDefinitionLine(line) else state.lines + line
          else state.lines
        State(updatedLines, nextInCommentBlock)
      }

    // Ensure we're using the correct file path and language
    return """|### File: $path
              |~~~$language
              |${finalState.lines.joinToString("\n")}
              |~~~"""
      .trimMargin()
  }

  /**
   * Finds the README file in the repository.
   *
   * @param repoDir The root directory of the repository
   * @return The README file if found, or null if not found
   */
  fun findReadmeFile(repoDir: File): String {
    val readmeFile =
      listOf("README.md", "Readme.md", "readme.md", "README.txt", "readme.txt")
        .map { File(repoDir, it) }
        .firstOrNull { it.exists() }

    return if (readmeFile != null) {
      val content = readmeFile.readText()
      logger.info("Readme file found: ${readmeFile.absolutePath}")
      logger.debug("Readme file content: $content")
      content
    } else {
      logger.warn("No readme file found in ${repoDir.absolutePath}")
      "No README content available."
    }
  }

  private fun findCodeFiles(dir: File): List<File> =
    when {
      dir.isHidden || dir.name == ".git" -> emptyList()
      !dir.isDirectory -> emptyList()
      else -> {
        val files = dir.listFiles() ?: return emptyList()
        files.flatMap { file ->
          when {
            file.isDirectory -> findCodeFiles(file)
            isCodeFile(file) -> listOf(file)
            else -> emptyList()
          }
        }
      }
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
}
