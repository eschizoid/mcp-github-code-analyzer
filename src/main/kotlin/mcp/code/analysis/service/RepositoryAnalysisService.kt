package mcp.code.analysis.service

/** Service for analyzing a Git repository. */
object RepositoryAnalysisService {

  /**
   * Analyzes a Git repository by cloning it, analyzing its structure, and generating insights.
   *
   * @param repoUrl The URL of the Git repository to analyze.
   * @param branch The branch of the repository to analyze (default is "main").
   * @param gitService The service for interacting with Git repositories (default is [GitService]).
   * @param codeAnalyzer The service for analyzing code (default is [CodeAnalyzer]).
   * @param modelContextService The service for generating insights using a model context (default is
   *   [ModelContextService]).
   * @return A string containing the analysis results or an error message.
   */
  suspend fun analyzeRepository(
    repoUrl: String,
    branch: String,
    gitService: GitService = GitService,
    codeAnalyzer: CodeAnalyzer = CodeAnalyzer,
    modelContextService: ModelContextService = ModelContextService,
  ): String {
    return try {
      val repoDir = gitService.cloneRepository(repoUrl, branch)
      val codeStructure = codeAnalyzer.analyzeStructure(repoDir)
      val codeSnippets = codeAnalyzer.collectAllCodeSnippets(repoDir)
      val readmeContent = codeAnalyzer.findReadmeFile(repoDir)?.readText() ?: ""
      val prompt = modelContextService.buildPrompt(codeSnippets)
      val insights = modelContextService.generateResponse(prompt).let { modelContextService.parseInsights(it) }
      modelContextService.generateSummary(codeStructure, insights, readmeContent)
    } catch (e: Exception) {
      "Error analyzing repository: ${e.message}"
    }
  }
}
