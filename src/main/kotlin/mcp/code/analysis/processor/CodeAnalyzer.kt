package mcp.code.analysis.processor

import java.io.File
import mcp.code.analysis.service.ModelContextService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

data class LanguagePatterns(
  val definitionPattern: Regex,
  val commentPrefixes: List<String>,
  val blockCommentStart: String,
  val blockCommentEnd: String,
)

data class ProcessingState(val lines: List<String> = emptyList(), val inCommentBlock: Boolean = false)

/**
 * Responsible for analyzing the structure of a codebase. Identifies files, directories, and their respective metadata
 * such as size, language, imports, and declarations.
 */
data class CodeAnalyzer(
  private val binaryFileSizeThreshold: Long = DEFAULT_BINARY_SIZE_THRESHOLD,
  private val binaryDetectionThreshold: Double = DEFAULT_BINARY_DETECTION_THRESHOLD,
  private val logger: Logger = LoggerFactory.getLogger(ModelContextService::class.java),
) {
  companion object {
    private const val DEFAULT_MAX_LINES = 100
    private const val SUMMARY_MAX_LINES = 250
    private const val DEFAULT_BINARY_SIZE_THRESHOLD = 1024 * 1024L // 1MB
    private const val DEFAULT_BINARY_DETECTION_THRESHOLD = 0.05

    private val README_FILENAMES =
      listOf("README.md", "Readme.md", "readme.md", "README.txt", "readme.txt", "README.rst", "README", "readme")

    private val SUPPORTED_EXTENSIONS = setOf("kt", "java", "scala", "py", "rb", "js", "ts", "go", "c", "cpp", "rs")

    private val CODE_EXTENSIONS =
      setOf(
        "kt",
        "java",
        "scala",
        "groovy",
        "clj",
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
        "html",
        "htm",
        "css",
        "scss",
        "sass",
        "less",
        "vue",
        "svelte",
        "json",
        "xml",
        "yaml",
        "yml",
        "toml",
        "properties",
        "ini",
        "conf",
        "md",
        "gradle",
        "gradle.kts",
        "sbt",
        "pom",
        "cmake",
        "make",
        "mk",
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

    private val IGNORED_DIRECTORIES =
      setOf(
        ".git",
        ".svn",
        ".hg",
        ".bzr",
        "node_modules",
        "target",
        "build",
        "dist",
        "out",
        ".gradle",
        ".idea",
        ".vscode",
      )
  }

  private val languagePatterns = createLanguagePatterns()

  fun collectSummarizedCodeSnippets(repoDir: File, maxLines: Int = DEFAULT_MAX_LINES): List<String> =
    findCodeFiles(repoDir)
      .filter { it.isRelevantCodeFile() && !it.isTestFile() }
      .mapNotNull { file ->
        try {
          val relativePath = file.getRelativePathFrom(repoDir)
          val language = getLanguageFromExtension(file.extension)
          val content = file.readText()
          summarizeCodeContent(relativePath, language, content, maxLines)
        } catch (e: Exception) {
          logger.warn("Error processing file ${file.absolutePath}: ${e.message}")
          null
        }
      }
      .also { logCollectionResults(it, repoDir) }

  fun summarizeCodeContent(path: String, language: String, content: String, maxLines: Int = SUMMARY_MAX_LINES): String {
    val patterns = languagePatterns[language.lowercase()] ?: languagePatterns["default"]!!

    val processor = CodeContentProcessor(patterns, maxLines)
    val processedLines = processor.processContent(content.lines())

    return formatCodeSummary(path, language, processedLines)
  }

  fun findReadmeFile(repoDir: File): String {
    val readmeFile = findFirstReadmeFile(repoDir)

    return readmeFile?.let { file ->
      try {
        val content = file.readText()
        logger.info("Readme file found: ${file.absolutePath}")
        logger.debug("Readme file content: $content")
        content
      } catch (e: Exception) {
        logger.info("Error reading readme file ${file.absolutePath}: ${e.message}")
        "No README content available."
      }
    }
      ?: run {
        logger.warn("No readme file found in ${repoDir.absolutePath}")
        "No README content available."
      }
  }

  private fun findCodeFiles(dir: File): List<File> =
    when {
      shouldIgnoreDirectory(dir) -> emptyList()
      !dir.isDirectory -> emptyList()
      else -> {
        val files = dir.listFiles() ?: return emptyList()
        files.flatMap { file ->
          when {
            file.isDirectory -> findCodeFiles(file)
            isCodeFile(file) && file.canRead() -> listOf(file)
            else -> emptyList()
          }
        }
      }
    }

  private fun shouldIgnoreDirectory(dir: File): Boolean = dir.isHidden || dir.name in IGNORED_DIRECTORIES

  private fun isCodeFile(file: File): Boolean = file.extension.lowercase() in CODE_EXTENSIONS

  private fun File.isRelevantCodeFile(): Boolean = extension.lowercase() in SUPPORTED_EXTENSIONS

  private fun File.isTestFile(): Boolean =
    absolutePath.contains("test", ignoreCase = true) || absolutePath.contains("spec", ignoreCase = true)

  private fun File.getRelativePathFrom(baseDir: File): String =
    absolutePath.removePrefix(baseDir.absolutePath).removePrefix(File.separator)

  private fun logCollectionResults(snippets: List<String>, repoDir: File) {
    logger.info("Collected ${snippets.size} summarized snippets from ${repoDir.absolutePath}")
    logger.debug("Snippets Found:\n${snippets.joinToString("\n")}")
  }

  private fun findFirstReadmeFile(repoDir: File): File? =
    README_FILENAMES.map { File(repoDir, it) }.firstOrNull { it.exists() && it.canRead() }

  private fun formatCodeSummary(path: String, language: String, lines: List<String>): String =
    """|### File: $path
       |~~~$language
       |${lines.joinToString("\n")}
       |~~~"""
      .trimMargin()

  private fun createLanguagePatterns(): Map<String, LanguagePatterns> =
    mapOf(
      "kotlin" to
        LanguagePatterns(
          Regex(
            "\\b(class|interface|object|enum\\s+class|data\\s+class|sealed\\s+class|fun|val|var|const|typealias|annotation\\s+class|import|package)\\b"
          ),
          listOf("//"),
          "/*",
          "*/",
        ),
      "scala" to
        LanguagePatterns(
          Regex(
            "\\b(class|object|trait|case\\s+class|case\\s+object|def|val|var|lazy\\s+val|type|implicit|sealed|abstract|override|package\\s+object|import|package)\\b"
          ),
          listOf("//"),
          "/*",
          "*/",
        ),
      "java" to
        LanguagePatterns(
          Regex(
            "\\b(class|interface|enum|@interface|record|public|private|protected|static|abstract|final|synchronized|volatile|native|transient|strictfp|void|import|package)\\b"
          ),
          listOf("//"),
          "/*",
          "*/",
        ),
      "python" to
        LanguagePatterns(
          Regex("\\b(def|class|async\\s+def)\\b|@\\w+|\\b(import|from)\\b"),
          listOf("#"),
          "\"\"\"",
          "\"\"\"",
        ),
      "ruby" to
        LanguagePatterns(
          Regex("\\b(def|class|module|attr_\\w+|require|include|extend)\\b"),
          listOf("#"),
          "=begin",
          "=end",
        ),
      "javascript" to
        LanguagePatterns(
          Regex("\\b(function|class|const|let|var|import|export|interface|type|enum|namespace)\\b"),
          listOf("//"),
          "/*",
          "*/",
        ),
      "typescript" to
        LanguagePatterns(
          Regex("\\b(function|class|const|let|var|import|export|interface|type|enum|namespace)\\b"),
          listOf("//"),
          "/*",
          "*/",
        ),
      "go" to
        LanguagePatterns(
          Regex("\\b(func|type|struct|interface|package|import|var|const)\\b"),
          listOf("//"),
          "/*",
          "*/",
        ),
      "rust" to
        LanguagePatterns(
          Regex("\\b(fn|struct|enum|trait|impl|pub|use|mod|const|static|type|async|unsafe)\\b"),
          listOf("//"),
          "/*",
          "*/",
        ),
      "c" to
        LanguagePatterns(
          Regex("\\b(struct|enum|typedef|void|int|char|bool|extern|static|class)\\b"),
          listOf("//"),
          "/*",
          "*/",
        ),
      "cpp" to
        LanguagePatterns(
          Regex("\\b(class|struct|enum|typedef|namespace|template|void|int|char|bool|auto|extern|static|virtual)\\b"),
          listOf("//"),
          "/*",
          "*/",
        ),
      "default" to
        LanguagePatterns(
          Regex("\\b(class|interface|object|enum|fun|def|function|public|private|protected|static)\\b"),
          listOf("//", "#"),
          "/*",
          "*/",
        ),
    )

  private fun getLanguageFromExtension(extension: String): String =
    when (extension.lowercase()) {
      "kt" -> "kotlin"
      "java" -> "java"
      "scala" -> "scala"
      "groovy" -> "groovy"
      "clj" -> "clojure"
      "py" -> "python"
      "rb" -> "ruby"
      "js",
      "jsx" -> "javascript"
      "ts",
      "tsx" -> "typescript"
      "php" -> "php"
      "pl",
      "pm" -> "perl"
      "sh",
      "bash" -> "shell"
      "ps1" -> "powershell"
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
      "html",
      "htm" -> "html"
      "css" -> "css"
      "scss",
      "sass" -> "sass"
      "less" -> "less"
      "vue" -> "vue"
      "svelte" -> "svelte"
      "json" -> "json"
      "xml" -> "xml"
      "yaml",
      "yml" -> "yaml"
      "toml" -> "toml"
      "md" -> "markdown"
      "csv" -> "csv"
      "gradle" -> "gradle"
      "gradle.kts" -> "gradle-kotlin"
      "properties" -> "properties"
      "ini" -> "ini"
      "conf" -> "conf"
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
