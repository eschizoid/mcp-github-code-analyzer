package mcp.code.analysis.service

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

      val readmeContent = codeAnalyzer.findReadmeFile(repoDir)
      val codeStructure = codeAnalyzer.analyzeStructure(repoDir)
      val codeSnippets = codeAnalyzer.collectAllCodeSnippets(repoDir)

      val insightsPrompt = modelContextService.buildInsightsPrompt(readmeContent)
      val insightsResponse = modelContextService.generateResponse(insightsPrompt)

      val summaryPrompt = modelContextService.buildSummaryPrompt(codeStructure, codeSnippets, insightsResponse)
      modelContextService.generateResponse(summaryPrompt)
    } catch (e: Exception) {
      throw Exception("Error analyzing repository: ${e.message}", e)
    }
  }
}
