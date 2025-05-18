package mcp.code.analysis.service

/** Service for analyzing Git repositories. */
class RepositoryAnalysisService {
  companion object {
    /**
     * Analyzes a Git repository and generates insights about its code structure and content.
     *
     * @param repoUrl The URL of the Git repository to analyze.
     * @param branch The branch of the repository to analyze (default is "main").
     * @return A summary of the analysis results.
     */
    suspend fun analyzeRepository(repoUrl: String, branch: String): String {
      try {
        val repoDir = GitService.cloneRepository(repoUrl, branch)
        val codeStructure = CodeAnalyzer.analyzeStructure(repoDir)
        val codeSnippets = CodeAnalyzer.collectAllCodeSnippets(repoDir)
        val readmeFile = CodeAnalyzer.findReadmeFile(repoDir)
        val readmeContent = readmeFile?.readText() ?: "No README found."
        val prompt = ModelContextService.buildPrompt(codeSnippets)
        val response = ModelContextService.generateResponse(prompt)
        val insights = ModelContextService.parseInsights(response)
        val summary = ModelContextService.generateSummary(codeStructure, insights, readmeContent)
        return summary
      } catch (e: Exception) {
        throw Exception("Error analyzing repository: ${e.message}", e)
      }
    }
  }
}
