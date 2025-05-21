package mcp.code.analysis.service

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.lang.Exception
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class RepositoryAnalysisServiceTest {
  @TempDir lateinit var tempDir: File

  private lateinit var gitService: GitService
  private lateinit var codeAnalyzer: CodeAnalyzer
  private lateinit var modelContextService: ModelContextService
  private lateinit var repositoryAnalysisService: RepositoryAnalysisService

  @BeforeEach
  fun setUp() {
    gitService = mockk()
    codeAnalyzer = mockk()
    modelContextService = mockk()

    repositoryAnalysisService =
      RepositoryAnalysisService(
        gitService = gitService,
        codeAnalyzer = codeAnalyzer,
        modelContextService = modelContextService,
      )
  }

  @Test
  fun `analyzeRepository should return analysis result`() = runTest {
    // Arrange
    val repoUrl = "https://github.com/test/repo"
    val branch = "main"
    val clonedRepo = File(tempDir, "repo")
    val readme = "# Test Repository"
    val codeSnippets = listOf("--- File: src/main.kt\n~~~kotlin\nfun main() {}\n~~~")
    val insightsPrompt = "Generated insights prompt"
    val insightsResponse = "File analysis..."
    val summaryPrompt = "Generated summary prompt"
    val summaryResponse = "Repository summary"

    // Mock behavior
    every { gitService.cloneRepository(repoUrl, branch) } returns clonedRepo
    every { codeAnalyzer.findReadmeFile(clonedRepo) } returns readme
    every { codeAnalyzer.collectAllCodeSnippets(clonedRepo) } returns codeSnippets
    every { modelContextService.buildInsightsPrompt(codeSnippets, readme) } returns insightsPrompt
    coEvery { modelContextService.generateResponse(insightsPrompt) } returns insightsResponse
    every { modelContextService.buildSummaryPrompt(insightsResponse) } returns summaryPrompt
    coEvery { modelContextService.generateResponse(summaryPrompt) } returns summaryResponse

    // Act
    val result = repositoryAnalysisService.analyzeRepository(repoUrl, branch)

    // Assert
    assertEquals(summaryResponse, result)
    verify { gitService.cloneRepository(repoUrl, branch) }
    verify { codeAnalyzer.findReadmeFile(clonedRepo) }
    verify { codeAnalyzer.collectAllCodeSnippets(clonedRepo) }
    verify { modelContextService.buildInsightsPrompt(codeSnippets, readme) }
    verify { modelContextService.buildSummaryPrompt(insightsResponse) }
  }

  @Test
  fun `analyzeRepository should handle git errors`() = runTest {
    // Arrange
    val repoUrl = "https://github.com/test/repo"
    val branch = "main"
    val errorMessage = "Repository not found"

    every { gitService.cloneRepository(repoUrl, branch) } throws Exception(errorMessage)

    // Act & Assert
    val exception = assertThrows<Exception> { repositoryAnalysisService.analyzeRepository(repoUrl, branch) }

    assertTrue(exception.message!!.contains("Error analyzing repository"))
    assertTrue(exception.cause?.message!!.contains(errorMessage))
  }

  @Test
  fun `analyzeRepository should handle code analysis errors`() = runTest {
    // Arrange
    val repoUrl = "https://github.com/test/repo"
    val branch = "main"
    val clonedRepo = File(tempDir, "repo")
    val errorMessage = "Error processing code files"

    every { gitService.cloneRepository(repoUrl, branch) } returns clonedRepo
    every { codeAnalyzer.findReadmeFile(clonedRepo) } returns "README content"
    every { codeAnalyzer.collectAllCodeSnippets(clonedRepo) } throws Exception(errorMessage)

    // Act & Assert
    val exception = assertThrows<Exception> { repositoryAnalysisService.analyzeRepository(repoUrl, branch) }

    assertTrue(exception.message!!.contains("Error analyzing repository"))
    assertTrue(exception.cause?.message!!.contains(errorMessage))
  }

  @Test
  fun `analyzeRepository should handle model service errors`() = runTest {
    // Arrange
    val repoUrl = "https://github.com/test/repo"
    val branch = "main"
    val clonedRepo = File(tempDir, "repo")
    val readme = "# Test Repository"
    val codeSnippets = listOf("--- File: src/main.kt\n~~~kotlin\nfun main() {}\n~~~")
    val insightsPrompt = "Generated insights prompt"
    val errorMessage = "Model API error"

    every { gitService.cloneRepository(repoUrl, branch) } returns clonedRepo
    every { codeAnalyzer.findReadmeFile(clonedRepo) } returns readme
    every { codeAnalyzer.collectAllCodeSnippets(clonedRepo) } returns codeSnippets
    every { modelContextService.buildInsightsPrompt(codeSnippets, readme) } returns insightsPrompt
    coEvery { modelContextService.generateResponse(insightsPrompt) } throws Exception(errorMessage)

    // Act & Assert
    val exception = assertThrows<Exception> { repositoryAnalysisService.analyzeRepository(repoUrl, branch) }

    assertTrue(exception.message!!.contains("Error analyzing repository"))
    assertTrue(exception.cause?.message!!.contains(errorMessage))
  }

  @Test
  fun `analyzeRepository should handle empty code snippets`() = runTest {
    // Arrange
    val repoUrl = "https://github.com/test/repo"
    val branch = "main"
    val clonedRepo = File(tempDir, "repo")
    val readme = "# Test Repository"
    val emptyCodeSnippets = emptyList<String>()
    val insightsPrompt = "Generated insights prompt with empty snippets"
    val insightsResponse = "Limited file analysis due to no code snippets"
    val summaryPrompt = "Generated summary prompt"
    val summaryResponse = "Limited repository summary"

    // Mock behavior
    every { gitService.cloneRepository(repoUrl, branch) } returns clonedRepo
    every { codeAnalyzer.findReadmeFile(clonedRepo) } returns readme
    every { codeAnalyzer.collectAllCodeSnippets(clonedRepo) } returns emptyCodeSnippets
    every { modelContextService.buildInsightsPrompt(emptyCodeSnippets, readme) } returns insightsPrompt
    coEvery { modelContextService.generateResponse(insightsPrompt) } returns insightsResponse
    every { modelContextService.buildSummaryPrompt(insightsResponse) } returns summaryPrompt
    coEvery { modelContextService.generateResponse(summaryPrompt) } returns summaryResponse

    // Act
    val result = repositoryAnalysisService.analyzeRepository(repoUrl, branch)

    // Assert
    assertEquals(summaryResponse, result)
    verify { codeAnalyzer.collectAllCodeSnippets(clonedRepo) }
    verify { modelContextService.buildInsightsPrompt(emptyCodeSnippets, readme) }
  }

  @Test
  fun `analyzeRepository should handle missing README`() = runTest {
    // Arrange
    val repoUrl = "https://github.com/test/repo"
    val branch = "main"
    val clonedRepo = File(tempDir, "repo")
    val noReadme = "No README content available."
    val codeSnippets = listOf("--- File: src/main.kt\n~~~kotlin\nfun main() {}\n~~~")
    val insightsPrompt = "Generated insights prompt without readme"
    val insightsResponse = "File analysis without readme context"
    val summaryPrompt = "Generated summary prompt"
    val summaryResponse = "Repository summary"

    // Mock behavior
    every { gitService.cloneRepository(repoUrl, branch) } returns clonedRepo
    every { codeAnalyzer.findReadmeFile(clonedRepo) } returns noReadme
    every { codeAnalyzer.collectAllCodeSnippets(clonedRepo) } returns codeSnippets
    every { modelContextService.buildInsightsPrompt(codeSnippets, noReadme) } returns insightsPrompt
    coEvery { modelContextService.generateResponse(insightsPrompt) } returns insightsResponse
    every { modelContextService.buildSummaryPrompt(insightsResponse) } returns summaryPrompt
    coEvery { modelContextService.generateResponse(summaryPrompt) } returns summaryResponse

    // Act
    val result = repositoryAnalysisService.analyzeRepository(repoUrl, branch)

    // Assert
    assertEquals(summaryResponse, result)
    verify { codeAnalyzer.findReadmeFile(clonedRepo) }
    verify { modelContextService.buildInsightsPrompt(codeSnippets, noReadme) }
  }
}
