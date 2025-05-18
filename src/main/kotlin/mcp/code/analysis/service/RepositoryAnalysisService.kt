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
      val codeStructure = codeAnalyzer.analyzeStructure(repoDir)
      val codeSnippets = codeAnalyzer.collectAllCodeSnippets(repoDir)
      val prompt = modelContextService.buildPrompt(codeSnippets)
      val response = modelContextService.generateResponse(prompt)
      val insights = modelContextService.parseInsights(response)
      val readmeContent = codeAnalyzer.findReadmeFile(repoDir)
      modelContextService.generateSummary(codeStructure, insights, readmeContent)
    } catch (e: Exception) {
      throw Exception("Error analyzing repository: ${e.message}", e)
    }
  }
}
