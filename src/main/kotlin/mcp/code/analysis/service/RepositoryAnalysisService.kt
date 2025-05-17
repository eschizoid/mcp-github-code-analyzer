package mcp.code.analysis.service

/** Service for analyzing a Git repository. */
class RepositoryAnalysisService {

  private val gitService = GitService()
  private val codeAnalyzer = CodeAnalyzer()
  private val modelContextService = ModelContextService()

  /**
   * Analyzes a Git repository and generates insights about its code structure and content.
   *
   * @param repoUrl The URL of the Git repository to analyze.
   * @param branch The branch of the repository to analyze (default is "main").
   * @return A summary of the analysis results.
   */
  suspend fun analyzeRepository(repoUrl: String, branch: String): String {

    try {
      // Clone repository
      val repoDir = gitService.cloneRepository(repoUrl, branch)

      // Analyze code structure
      val codeStructure = codeAnalyzer.analyzeStructure(repoDir)

      // Generate insights using appropriate methods
      val codeSnippets = codeAnalyzer.collectAllCodeSnippets(repoDir)

      // Get README file content
      val readmeFile = codeAnalyzer.findReadmeFile(repoDir)
      val readmeContent = readmeFile?.readText() ?: "No README found."

      // Build prompt for model context
      val prompt = modelContextService.buildPrompt(codeSnippets)

      // Generate response using model context
      val response = modelContextService.generateResponse(prompt)

      // Parse response to extract insights
      val insights = modelContextService.parseInsights(response)

      // Generate summary using model context
      val summary = modelContextService.generateSummary(codeStructure, insights, readmeContent)

      return summary
    } catch (e: Exception) {
      throw Exception("Error analyzing repository: ${e.message}", e)
    }
  }
}
