package mcp.code.analysis.service

import java.io.File

class CodeAnalyzer {
    fun analyzeStructure(repoDir: File): Map<String, Any> {
        val structure = mutableMapOf<String, Any>()
        val rootPath = repoDir.absolutePath

        // Process directory structure
        processDirectory(repoDir, structure, rootPath)

        return structure
    }

    private fun processDirectory(dir: File, structure: MutableMap<String, Any>, rootPath: String) {
        // Skip hidden directories and common directories to ignore
        val dirsToIgnore = setOf(".git", "node_modules", "venv", "__pycache__", "target", "build", "dist", ".idea", ".vscode")
        if (dir.isHidden || dir.name in dirsToIgnore) {
            return
        }

        val files = dir.listFiles() ?: return

        for (file in files) {
            val relativePath = file.absolutePath.substring(rootPath.length + 1)

            if (file.isDirectory) {
                val dirStructure = mutableMapOf<String, Any>()
                structure[relativePath] = dirStructure
                processDirectory(file, dirStructure, rootPath)
            } else {
                // For code files, add metadata
                val metadata = analyzeFile(file)
                structure[relativePath] = metadata
            }
        }
    }

    private fun analyzeFile(file: File): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        result["size"] = file.length()
        result["extension"] = file.extension

        // Skip files that are too large (e.g., binary files, generated code)
        if (file.length() > 1024 * 1024) { // Skip files larger than 1MB
            result["skipped"] = "File too large"
            return result
        }

        // Skip binary files
        if (isBinaryFile(file)) {
            result["skipped"] = "Binary file"
            return result
        }

        try {
            val content = file.readText()
            result["lines"] = content.lines().size

            val language = getLanguageFromExtension(file.extension)
            result["language"] = language

            // Extract language-specific information
            result["imports"] = extractImports(content, language)

            // Extract classes, functions, methods based on language
            val declarations = extractDeclarations(content, language)
            declarations.forEach { (key, value) ->
                result[key] = value
            }

        } catch (e: Exception) {
            result["error"] = "Failed to analyze: ${e.message}"
        }

        return result
    }

    private fun isBinaryFile(file: File): Boolean {
        // Check if file is likely binary by looking at the first few bytes
        val binaryExtensions = setOf(
            "class", "jar", "war", "ear", "zip", "tar", "gz", "rar",
            "exe", "dll", "so", "dylib", "obj", "o", "a", "lib",
            "png", "jpg", "jpeg", "gif", "bmp", "ico", "svg",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
        )

        // Quick check based on extension
        if (file.extension.lowercase() in binaryExtensions) {
            return true
        }

        // More thorough check by examining content
        try {
            val bytes = file.readBytes().take(1000).toByteArray()
            var nullCount = 0

            for (byte in bytes) {
                if (byte == 0.toByte()) {
                    nullCount++
                }
            }

            // If more than 5% of the first 1000 bytes are null, likely binary
            return nullCount > bytes.size * 0.05
        } catch (e: Exception) {
            return false
        }
    }

    private fun getLanguageFromExtension(extension: String): String {
        return when (extension.lowercase()) {
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
            "pl", "pm" -> "perl"
            "sh", "bash" -> "shell"
            "ps1" -> "powershell"

            // Systems programming
            "c" -> "c"
            "cpp", "cc", "cxx" -> "cpp"
            "h", "hpp" -> "cpp-header"
            "cs" -> "csharp"
            "go" -> "go"
            "rs" -> "rust"
            "swift" -> "swift"
            "m" -> "objective-c"

            // Web
            "html", "htm" -> "html"
            "css" -> "css"
            "scss", "sass" -> "sass"
            "less" -> "less"
            "vue" -> "vue"
            "svelte" -> "svelte"

            // Data formats
            "json" -> "json"
            "xml" -> "xml"
            "yaml", "yml" -> "yaml"
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
            "ex", "exs" -> "elixir"
            "erl", "hrl" -> "erlang"
            "hs" -> "haskell"
            "fs", "fsx" -> "fsharp"

            else -> "text"
        }
    }

    private fun extractImports(content: String, language: String): List<String> {
        return when (language) {
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
                val imports = importRegex.findAll(content).flatMap { result ->
                    result.groupValues[1].split(",").map { it.trim().split(".").first() }
                }

                (fromImports + imports).toList()
            }
            "javascript", "typescript" -> {
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
                val multiImports = multiImportRegex.findAll(content).flatMap { result ->
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

                if (namespace != null) {
                    listOf(namespace) + uses
                } else {
                    uses
                }
            }
            else -> emptyList()
        }
    }

    private fun extractDeclarations(content: String, language: String): Map<String, List<String>> {
        val declarations = mutableMapOf<String, List<String>>()

        when (language) {
            "kotlin" -> {
                // Classes, interfaces, objects, and data classes
                val classRegex = Regex("(?:class|interface|object|enum class|data class)\\s+(\\w+)(?:<[^>]*>)?[^{]*\\{")
                declarations["classes"] = classRegex.findAll(content).map { it.groupValues[1] }.toList()

                // Functions
                val functionRegex = Regex("(?:fun|suspend fun)\\s+(?:<[^>]*>\\s+)?(\\w+)\\s*(?:<[^>]*>)?\\s*\\([^)]*\\)(?:\\s*:\\s*[\\w<>?.,\\s]+)?\\s*(?:\\{|=)")
                declarations["functions"] = functionRegex.findAll(content).map { it.groupValues[1] }.toList()
            }
            "java" -> {
                // Classes and interfaces
                val classRegex = Regex("(?:public|private|protected|)\\s*(?:class|interface|enum)\\s+(\\w+)(?:<[^>]*>)?[^{]*\\{")
                declarations["classes"] = classRegex.findAll(content).map { it.groupValues[1] }.toList()

                // Methods
                val methodRegex = Regex("(?:public|private|protected|static|final|native|synchronized|abstract|transient)?\\s*(?:<[^>]*>)?\\s*(?:[\\w<>\\[\\]]+)\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w,\\s]+)?\\s*\\{")
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
            "javascript", "typescript" -> {
                // Classes
                val classRegex = Regex("class\\s+(\\w+)(?:\\s+extends\\s+\\w+)?\\s*\\{")
                declarations["classes"] = classRegex.findAll(content).map { it.groupValues[1] }.toList()

                // Functions (including arrow functions with explicit names)
                val functionRegex = Regex("(?:function\\s+(\\w+)|(?:const|let|var)\\s+(\\w+)\\s*=\\s*function|(?:const|let|var)\\s+(\\w+)\\s*=\\s*\\([^)]*\\)\\s*=>)")
                declarations["functions"] = functionRegex.findAll(content).mapNotNull {
                    it.groupValues[1].ifEmpty {
                        it.groupValues[2].ifEmpty {
                            it.groupValues[3]
                        }
                    }
                }.filter { it.isNotEmpty() }.toList()

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
                val functionRegex = Regex("(?:def|val|var)\\s+(\\w+)(?:\\[[^\\]]*\\])?\\s*(?:\\([^)]*\\))?(?:\\s*:\\s*[^=]*)?\\s*=")
                declarations["functions"] = functionRegex.findAll(content).map { it.groupValues[1] }.toList()
            }
            "rust" -> {
                // Structs and enums
                val structRegex = Regex("(?:pub\\s+)?struct\\s+(\\w+)(?:<[^>]*>)?[^;{]*[{;]")
                declarations["structs"] = structRegex.findAll(content).map { it.groupValues[1] }.toList()

                val enumRegex = Regex("(?:pub\\s+)?enum\\s+(\\w+)(?:<[^>]*>)?\\s*\\{")
                declarations["enums"] = enumRegex.findAll(content).map { it.groupValues[1] }.toList()

                // Functions and methods
                val functionRegex = Regex("(?:pub\\s+)?(?:async\\s+)?fn\\s+(\\w+)(?:<[^>]*>)?\\s*\\([^)]*\\)(?:\\s*->\\s*[^{]+)?\\s*\\{")
                declarations["functions"] = functionRegex.findAll(content).map { it.groupValues[1] }.toList()

                // Traits (interfaces)
                val traitRegex = Regex("(?:pub\\s+)?trait\\s+(\\w+)(?:<[^>]*>)?\\s*(?:\\{|:|where)")
                declarations["traits"] = traitRegex.findAll(content).map { it.groupValues[1] }.toList()
            }
            "cpp", "c" -> {
                // Classes and structs
                val classRegex = Regex("(?:class|struct)\\s+(\\w+)(?::[^{]+)?\\s*\\{")
                declarations["classes"] = classRegex.findAll(content).map { it.groupValues[1] }.toList()

                // Functions
                val functionRegex = Regex("(?:[\\w:]+\\s+)+([\\w~]+)\\s*\\([^)]*\\)(?:\\s*const)?(?:\\s*noexcept)?(?:\\s*override)?\\s*(?:\\{|;)")
                declarations["functions"] = functionRegex.findAll(content)
                    .map { it.groupValues[1] }
                    .filter { it !in setOf("if", "for", "while", "switch", "catch") } // Filter out control structures
                    .toList()
            }
        }

        return declarations
    }
}