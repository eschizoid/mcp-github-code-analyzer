package mcp.code.analysis.service

import java.io.File
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
   * Analyzes the structure of a codebase.
   *
   * @param repoDir The root directory of the repository to analyze
   * @return A map representing the directory structure and metadata of files
   */
  fun analyzeStructure(repoDir: File): Map<String, Any> = processDirectory(repoDir, repoDir.absolutePath)

  /**
   * Collects all code snippets from the repository.
   *
   * @param repoDir The root directory of the repository
   * @return List of code snippets with metadata including file path and language
   */
  fun collectAllCodeSnippets(repoDir: File): List<String> =
    findCodeFiles(repoDir)
      .filter { file ->
        file.extension.lowercase() in setOf("kt", "java", "scala", "py", "rb", "js", "ts", "go", "c", "cpp", "rust") &&
          !file.absolutePath.contains("test", ignoreCase = true)
      }
      .map { file ->
        val relativePath = file.absolutePath.substring(repoDir.absolutePath.length + 1)
        val lang = getLanguageFromExtension(file.extension)
        val safeContent = file.readLines().joinToString("\n")
        "---\nFile: $relativePath\n~~~$lang\n$safeContent\n~~~"
      }
      .toList()
      .also { snippets ->
        logger.info("Collected ${snippets.size} code snippets from ${repoDir.absolutePath}")
        logger.debug("Snippets Found:\n${snippets.joinToString("\n")}")
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

  private fun processDirectory(dir: File, rootPath: String): Map<String, Any> {
    // Skip hidden directories and common directories to ignore
    val dirsToIgnore =
      setOf(".git", "node_modules", "venv", "__pycache__", "target", "build", "dist", ".idea", ".vscode")

    if (dir.isHidden || dir.name in dirsToIgnore) return emptyMap()
    val files = dir.listFiles()?.toList() ?: return emptyMap()
    val fileEntries = files.filterNot(File::isDirectory).associate { file -> file.name to analyzeFile(file) }

    val directoryEntries =
      files
        .filter(File::isDirectory)
        .flatMap { subDir ->
          val dirStructure = processDirectory(subDir, rootPath)
          if (dirStructure.isEmpty()) emptyList()
          else {
            val relativePath = subDir.absolutePath.substring(rootPath.length + 1)
            listOf(relativePath to dirStructure)
          }
        }
        .toMap()

    return fileEntries + directoryEntries
  }

  private fun analyzeFile(file: File): Map<String, Any> {
    // Prepare initial metadata
    val fileSize = file.length()

    // Skip large or binary files early
    when {
      fileSize > binaryFileSizeThreshold ->
        return mapOf("size" to fileSize, "extension" to file.extension, "skipped" to "File too large")

      isBinaryFile(file) -> return mapOf("size" to fileSize, "extension" to file.extension, "skipped" to "Binary file")
    }

    return try {
      val content = file.readText()
      val language = getLanguageFromExtension(file.extension)
      val imports = extractImports(content, language)
      val declarations = extractDeclarations(content, language)

      mapOf(
        "size" to fileSize,
        "extension" to file.extension,
        "lines" to content.lines().size,
        "language" to language,
        "imports" to imports,
      ) + declarations
    } catch (e: Exception) {
      mapOf("size" to fileSize, "extension" to file.extension, "error" to "Failed to analyze: ${e.message}")
    }
  }

  private fun isBinaryFile(file: File): Boolean {
    // Check if a file is likely a binary by looking at the first few bytes
    val binaryExtensions =
      setOf(
        "class",
        "jar",
        "war",
        "ear",
        "zip",
        "tar",
        "gz",
        "rar",
        "exe",
        "dll",
        "so",
        "dylib",
        "obj",
        "o",
        "a",
        "lib",
        "png",
        "jpg",
        "jpeg",
        "gif",
        "bmp",
        "ico",
        "svg",
        "pdf",
        "doc",
        "docx",
        "xls",
        "xlsx",
        "ppt",
        "pptx",
      )

    // Quick check based on extension
    if (file.extension.lowercase() in binaryExtensions) return true

    // More thorough check by examining content
    return try {
      val bytes = file.readBytes().take(1000).toByteArray()
      val nullCount = bytes.count { it == 0.toByte() }

      // If more than the threshold percentage of the first 1000 bytes is null, likely binary
      nullCount > bytes.size * binaryDetectionThreshold
    } catch (_: Exception) {
      false
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

  private fun extractImports(content: String, language: String): List<String> =
    when (language) {
      "kotlin" -> {
        val regex = Regex("import\\s+([\\w.]+)(?:\\s+as\\s+\\w+)?.*")
        regex.findAll(content).map { it.groupValues[1] }.toList()
      }

      "java" -> {
        val regex = Regex("import\\s+(?:static\\s+)?([\\w.]+)(?:\\.[*])?;")
        regex.findAll(content).map { it.groupValues[1] }.toList()
      }

      "python" -> {
        val fromImportRegex = Regex("from\\s+([\\w.]+)\\s+import\\s+.+")
        val importRegex = Regex("import\\s+([\\w.,\\s]+)")

        val fromImports = fromImportRegex.findAll(content).map { it.groupValues[1] }
        val imports =
          importRegex.findAll(content).flatMap { result ->
            result.groupValues[1].split(",").map { it.trim().split(".").first() }
          }

        (fromImports + imports).toList()
      }

      "javascript",
      "typescript" -> {
        val es6ImportRegex = Regex("import\\s+(?:(?:\\{[^}]*\\}|\\w+)\\s+from\\s+)?['\"]([^'\"]+)['\"]")
        val requireRegex = Regex("(?:const|let|var)\\s+.+\\s*=\\s*require\\(['\"]([^'\"]+)['\"]\\)")

        val es6Imports = es6ImportRegex.findAll(content).map { it.groupValues[1] }
        val requires = requireRegex.findAll(content).map { it.groupValues[1] }

        (es6Imports + requires).toList()
      }

      "go" -> {
        val singleImportRegex = Regex("import\\s+[\"']([\\w./]+)[\"']")
        val multiImportRegex = Regex("import\\s+\\(([^)]+)\\)")

        val singleImports = singleImportRegex.findAll(content).map { it.groupValues[1] }
        val multiImports =
          multiImportRegex.findAll(content).flatMap { result ->
            val importBlock = result.groupValues[1]
            Regex("[\"']([\\w./]+)[\"']").findAll(importBlock).map { it.groupValues[1] }
          }

        (singleImports + multiImports).toList()
      }

      "ruby" -> {
        val regex = Regex("require(?:_relative)?\\s+['\"]([^'\"]+)['\"]")
        regex.findAll(content).map { it.groupValues[1] }.toList()
      }

      "scala" -> {
        val regex = Regex("import\\s+([\\w.{}=>,$\\s]+)")
        regex.findAll(content).map { it.groupValues[1].trim() }.toList()
      }

      "rust" -> {
        val regex = Regex("use\\s+([\\w:]+)(?:::\\{[^}]*\\})?;")
        regex.findAll(content).map { it.groupValues[1] }.toList()
      }

      "php" -> {
        val namespaceRegex = Regex("namespace\\s+([\\w\\\\]+);")
        val useRegex = Regex("use\\s+([\\w\\\\]+)(?:\\s+as\\s+\\w+)?;")

        val namespace = namespaceRegex.find(content)?.groupValues?.get(1)
        val uses = useRegex.findAll(content).map { it.groupValues[1] }.toList()

        if (namespace != null) listOf(namespace) + uses else uses
      }

      else -> emptyList()
    }

  private fun extractDeclarations(content: String, language: String): Map<String, List<String>> {
    val declarations = mutableMapOf<String, List<String>>()

    when (language) {
      "kotlin" -> {
        // Classes, interfaces, objects, and data classes
        val classRegex = Regex("(?:class|interface|object|enum class|data class)\\s+(\\w+)(?:<[^>]*>)?[^{]*\\{")
        declarations["classes"] = classRegex.findAll(content).map { it.groupValues[1] }.toList()

        // Functions
        val functionRegex =
          Regex(
            "(?:fun|suspend fun)\\s+(?:<[^>]*>\\s+)?(\\w+)\\s*(?:<[^>]*>)?\\s*\\([^)]*\\)(?:\\s*:\\s*[\\w<>?.,\\s]+)?\\s*(?:\\{|=)"
          )
        declarations["functions"] = functionRegex.findAll(content).map { it.groupValues[1] }.toList()
      }

      "java" -> {
        // Classes and interfaces
        val classRegex =
          Regex("(?:public|private|protected|)\\s*(?:class|interface|enum)\\s+(\\w+)(?:<[^>]*>)?[^{]*\\{")
        declarations["classes"] = classRegex.findAll(content).map { it.groupValues[1] }.toList()

        // Methods
        val methodRegex =
          Regex(
            "(?:public|private|protected|static|final|native|synchronized|abstract|transient)?\\s*(?:<[^>]*>)?\\s*(?:[\\w<>\\[\\]]+)\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w,\\s]+)?\\s*\\{"
          )
        declarations["methods"] = methodRegex.findAll(content).map { it.groupValues[1] }.toList()
      }

      "python" -> {
        // Classes
        val classRegex = Regex("class\\s+(\\w+)(?:\\([^)]*\\))?\\s*:")
        declarations["classes"] = classRegex.findAll(content).map { it.groupValues[1] }.toList()

        // Functions and methods
        val functionRegex = Regex("def\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:->\\s*[^:]+)?\\s*:")
        declarations["functions"] = functionRegex.findAll(content).map { it.groupValues[1] }.toList()
      }

      "go" -> {
        // Structs
        val structRegex = Regex("type\\s+(\\w+)\\s+struct\\s*\\{")
        declarations["structs"] = structRegex.findAll(content).map { it.groupValues[1] }.toList()

        // Interfaces
        val interfaceRegex = Regex("type\\s+(\\w+)\\s+interface\\s*\\{")
        declarations["interfaces"] = interfaceRegex.findAll(content).map { it.groupValues[1] }.toList()

        // Functions
        val functionRegex = Regex("func\\s+(?:\\([^)]+\\)\\s+)?(\\w+)\\s*\\([^)]*\\)\\s*(?:\\([^)]*\\))?\\s*\\{")
        declarations["functions"] = functionRegex.findAll(content).map { it.groupValues[1] }.toList()
      }

      "javascript",
      "typescript" -> {
        // Classes
        val classRegex = Regex("class\\s+(\\w+)(?:\\s+extends\\s+\\w+)?\\s*\\{")
        declarations["classes"] = classRegex.findAll(content).map { it.groupValues[1] }.toList()

        // Functions (including arrow functions with explicit names)
        val functionRegex =
          Regex(
            "function\\s+(\\w+)|(?:const|let|var)\\s+(\\w+)\\s*=\\s*function|(?:const|let|var)\\s+(\\w+)\\s*=\\s*\\([^)]*\\)\\s*=>"
          )
        declarations["functions"] =
          functionRegex
            .findAll(content)
            .map { it.groupValues[1].ifEmpty { it.groupValues[2].ifEmpty { it.groupValues[3] } } }
            .filter { it.isNotEmpty() }
            .toList()

        // For TypeScript: interfaces and types
        if (language == "typescript") {
          val interfaceRegex = Regex("interface\\s+(\\w+)(?:<[^>]*>)?\\s*(?:extends\\s+[^{]+)?\\s*\\{")
          declarations["interfaces"] = interfaceRegex.findAll(content).map { it.groupValues[1] }.toList()

          val typeRegex = Regex("type\\s+(\\w+)(?:<[^>]*>)?\\s*=")
          declarations["types"] = typeRegex.findAll(content).map { it.groupValues[1] }.toList()
        }
      }

      "scala" -> {
        // Classes, objects, and traits
        val classRegex = Regex("(?:class|object|trait|case class)\\s+(\\w+)(?:\\[[^\\]]*\\])?[^{]*\\{")
        declarations["classes"] = classRegex.findAll(content).map { it.groupValues[1] }.toList()

        // Methods and functions (including vals/vars with function values)
        val functionRegex =
          Regex("(?:def|val|var)\\s+(\\w+)(?:\\[[^\\]]*\\])?\\s*(?:\\([^)]*\\))?(?:\\s*:\\s*[^=]*)?\\s*=")
        declarations["functions"] = functionRegex.findAll(content).map { it.groupValues[1] }.toList()
      }

      "rust" -> {
        // Structs and enums
        val structRegex = Regex("(?:pub\\s+)?struct\\s+(\\w+)(?:<[^>]*>)?[^;{]*[{;]")
        declarations["structs"] = structRegex.findAll(content).map { it.groupValues[1] }.toList()

        val enumRegex = Regex("(?:pub\\s+)?enum\\s+(\\w+)(?:<[^>]*>)?\\s*\\{")
        declarations["enums"] = enumRegex.findAll(content).map { it.groupValues[1] }.toList()

        // Functions and methods
        val functionRegex =
          Regex("(?:pub\\s+)?(?:async\\s+)?fn\\s+(\\w+)(?:<[^>]*>)?\\s*\\([^)]*\\)(?:\\s*->\\s*[^{]+)?\\s*\\{")
        declarations["functions"] = functionRegex.findAll(content).map { it.groupValues[1] }.toList()

        // Traits (interfaces)
        val traitRegex = Regex("(?:pub\\s+)?trait\\s+(\\w+)(?:<[^>]*>)?\\s*(?:\\{|:|where)")
        declarations["traits"] = traitRegex.findAll(content).map { it.groupValues[1] }.toList()
      }

      "cpp",
      "c" -> {
        // Classes and structs
        val classRegex = Regex("(?:class|struct)\\s+(\\w+)(?::[^{]+)?\\s*\\{")
        declarations["classes"] = classRegex.findAll(content).map { it.groupValues[1] }.toList()

        // Functions
        val functionRegex =
          Regex("(?:[\\w:]+\\s+)+([\\w~]+)\\s*\\([^)]*\\)(?:\\s*const)?(?:\\s*noexcept)?(?:\\s*override)?\\s*(?:\\{|;)")
        declarations["functions"] =
          functionRegex
            .findAll(content)
            .map { it.groupValues[1] }
            .filter { it !in setOf("if", "for", "while", "switch", "catch") } // Filter out control structures
            .toList()
      }
    }

    return declarations
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
