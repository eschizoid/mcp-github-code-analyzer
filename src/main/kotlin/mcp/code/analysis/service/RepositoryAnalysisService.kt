package mcp.code.analysis.service

import mcp.code.analysis.processor.CodeAnalyzer

/** Service for analyzing Git repositories. */
data class RepositoryAnalysisService(
  private val gitService: GitService = GitService(),
  private val codeAnalyzer: CodeAnalyzer = CodeAnalyzer(),
  private val modelContextService: ModelContextService = ModelContextService(),
) {

  /**
   * Analyzes a Git repository and generates insights about its code structure and content.
   *
   * @param repoUrl The URL of the Git repository to analyze.
   * @param branch The branch of the repository to analyze.
   * @return A summary of the analysis results.
   */
  suspend fun analyzeRepository(repoUrl: String, branch: String): String {
    return try {
      val repoDir = gitService.cloneRepository(repoUrl, branch)

      val readme = codeAnalyzer.findReadmeFile(repoDir)
      val codeSnippets = codeAnalyzer.collectSummarizedCodeSnippets(repoDir)

      val insightsPrompt = modelContextService.buildInsightsPrompt(codeSnippets, readme)
      val insightsResponse = modelContextService.generateResponse(insightsPrompt)

      val summaryPrompt = modelContextService.buildSummaryPrompt(insightsResponse)
      val summaryResponse = modelContextService.generateResponse(summaryPrompt)

      summaryResponse
    } catch (e: Exception) {
      throw Exception("Error analyzing repository: ${e.message}", e)
    }
  }
}
