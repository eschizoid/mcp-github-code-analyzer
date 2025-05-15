package mcp.code.analysis.service

import java.io.File
import kotlinx.serialization.json.*
import mcp.code.analysis.service.lsp.LanguageAnalysisService

/**
 * RepositoryASTAnalyzer is responsible for analyzing the AST of files in a Git repository. It uses
 * the LanguageAnalysisService to parse files and generate their ASTs.
 *
 * @param workDir The working directory where the repository will be cloned.
 * @param gitService The service used to interact with Git repositories.
 */
class RepositoryAstAnalyzer(private val workDir: File, private val gitService: GitService) {
  private val languageAnalysisService = LanguageAnalysisService(workDir)

  /**
   * Analyzes the AST of files in a Git repository.
   *
   * @param repoUrl The URL of the Git repository to analyze.
   * @param branch The branch of the repository to analyze (default is "main").
   * @return A map where keys are file paths and values are their corresponding ASTs.
   */
  fun analyzeRepository(repoUrl: String, branch: String = "main"): Map<String, JsonElement> {
    val repoDir = gitService.cloneRepository(repoUrl, branch)
    try {
      return analyzeRepository(repoDir)
    } finally {
      // Ensure we shut down LSP servers when done
      languageAnalysisService.shutdown()
    }
  }

  /**
   * Analyzes the AST of files in a given directory.
   *
   * @param repoDir The directory containing the files to analyze.
   * @return A map where keys are file paths and values are their corresponding ASTs.
   */
  fun analyzeRepository(repoDir: File): Map<String, JsonElement> {
    val results = mutableMapOf<String, JsonElement>()

    repoDir
      .walkTopDown()
      .filter { it.isFile && !it.isHidden && shouldAnalyzeFile(it) }
      .forEach { file ->
        val relativePath = file.relativeTo(repoDir).path
        try {
          val ast = languageAnalysisService.parseFile(file)
          results[relativePath] = ast
        } catch (e: Exception) {
          results[relativePath] =
            JsonObject(mapOf("error" to JsonPrimitive("Failed to parse: ${e.message}")))
        }
      }

    return results
  }

  private fun shouldAnalyzeFile(file: File): Boolean {
    val excludedDirs = setOf(".git", "node_modules", "build", "target", "dist")
    val path = file.path
    return excludedDirs.none { path.contains("/$it/") } && file.length() < 1024 * 1024
  }
}
