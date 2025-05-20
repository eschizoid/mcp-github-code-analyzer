package mcp.code.analysis.service

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.lang.Exception
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
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
    val repoUrl = "https://github.com/user/repo"
    val branch = "main"
    val clonedRepo = File(tempDir, "repo")
    val readmeContent = "# Test Repository"
    val codeSnippets =
      listOf(
        """|---
           |File: src/Main.kt
           |~~~kotlin
           |fun main() {}
           |~~~"""
          .trimMargin()
      )
    val insightsPrompt = "insights prompt"
    val summaryPrompt = "summary prompt"
    val insightsResponse = "insights"
    val summaryResponse = "summary"

    every { gitService.cloneRepository(repoUrl, branch) } returns clonedRepo
    every { codeAnalyzer.findReadmeFile(clonedRepo) } returns readmeContent
    every { modelContextService.buildInsightsPrompt(readmeContent) } returns insightsPrompt
    every { modelContextService.buildSummaryPrompt(any(), any()) } returns summaryPrompt
    every { codeAnalyzer.analyzeStructure(clonedRepo) } returns emptyMap()
    every { codeAnalyzer.collectAllCodeSnippets(clonedRepo) } returns codeSnippets
    coEvery { modelContextService.generateResponse(insightsPrompt) } returns insightsResponse
    coEvery { modelContextService.generateResponse(summaryPrompt) } returns summaryResponse

    // Act
    val result = repositoryAnalysisService.analyzeRepository(repoUrl, branch)

    // Assert
    assertTrue(result.contains(insightsResponse))
    assertTrue(result.contains(summaryResponse))
    verify { gitService.cloneRepository(repoUrl, branch) }
    verify { codeAnalyzer.findReadmeFile(clonedRepo) }
    verify { codeAnalyzer.collectAllCodeSnippets(clonedRepo) }
    verify { runBlocking { modelContextService.generateResponse(any()) } }
  }

  @Test
  fun `analyzeRepository should handle git errors`() = runTest {
    // Arrange
    val repoUrl = "https://github.com/user/repo"
    val branch = "main"

    every { gitService.cloneRepository(repoUrl, branch) } throws Exception("Error cloning repository")

    // Act & Assert
    val exception = assertThrows<Exception> { repositoryAnalysisService.analyzeRepository(repoUrl, branch) }
    assert(exception.message?.contains("Error cloning repository") == true)
  }

  @Test
  fun `analyzeRepository should handle code analysis errors`() {
    // Arrange
    val repoUrl = "https://github.com/user/repo"
    val branch = "main"
    val clonedRepo = File(tempDir, "repo")

    every { gitService.cloneRepository(repoUrl, branch) } returns clonedRepo
    every { codeAnalyzer.findReadmeFile(clonedRepo) } throws Exception("Error analyzing repository code")

    // Act & Assert
    val exception =
      assertThrows<Exception> { runBlocking { repositoryAnalysisService.analyzeRepository(repoUrl, branch) } }

    assert(exception.message?.contains("Error analyzing repository code") == true)
  }

  @Test
  fun `analyzeRepository should handle model service errors`() {
    // Arrange
    val repoUrl = "https://github.com/user/repo"
    val branch = "main"
    val clonedRepo = File(tempDir, "repo")
    val readmeContent = "# Test Repository"
    val codeSnippets =
      listOf(
        """|---
           |File: src/Main.kt
           |~~~kotlin
           |fun main() {}
           |~~~"""
          .trimMargin()
      )

    every { gitService.cloneRepository(repoUrl, branch) } returns clonedRepo
    every { codeAnalyzer.findReadmeFile(clonedRepo) } returns readmeContent
    every { codeAnalyzer.collectAllCodeSnippets(clonedRepo) } returns codeSnippets
    coEvery { modelContextService.generateResponse(any()) } throws Exception("Error analyzing repository")

    // Act & Assert
    val exception =
      assertThrows<Exception> { runBlocking { repositoryAnalysisService.analyzeRepository(repoUrl, branch) } }

    assert(exception.message?.contains("Error analyzing repository") == true)
  }

  @Test
  fun `analyzeRepository should handle empty code snippets`() = runTest {
    // Arrange
    val repoUrl = "https://github.com/user/repo"
    val branch = "main"
    val clonedRepo = File(tempDir, "repo")
    val readmeContent = "# Test Repository"
    val emptySnippets = emptyList<String>()
    val insightsPrompt = "Insights prompt"
    val summaryPrompt = "Summary prompt"
    val modelResponse = "Repository Analysis"

    every { gitService.cloneRepository(repoUrl, branch) } returns clonedRepo
    every { codeAnalyzer.findReadmeFile(clonedRepo) } returns readmeContent
    every { codeAnalyzer.analyzeStructure(clonedRepo) } returns emptyMap()
    every { codeAnalyzer.collectAllCodeSnippets(clonedRepo) } returns emptySnippets
    every { modelContextService.buildInsightsPrompt(readmeContent) } returns insightsPrompt
    every { modelContextService.buildSummaryPrompt(any(), any()) } returns summaryPrompt
    coEvery { modelContextService.generateResponse(any()) } returns modelResponse

    // Act
    val result = repositoryAnalysisService.analyzeRepository(repoUrl, branch)

    // Assert
    assertEquals(
      """|$modelResponse
       |
       |$modelResponse
       |"""
        .trimMargin(),
      result,
    )
    verify { codeAnalyzer.collectAllCodeSnippets(clonedRepo) }
    verify {
      runBlocking { modelContextService.generateResponse("Insights prompt") }
      runBlocking { modelContextService.generateResponse("Summary prompt") }
    }
  }

  @Test
  fun `analyzeRepository should handle missing README`() = runTest {
    // Arrange
    val repoUrl = "https://github.com/user/repo"
    val branch = "main"
    val clonedRepo = File(tempDir, "repo")
    val noReadme = "No README content available."
    val codeSnippets =
      listOf(
        """"|---
            |File: src/Main.kt
            |~~~kotlin
            |fun main() {}
            |~~~"""
          .trimMargin()
      )
    val modelResponse = "Repository Analysis: Kotlin project without README"
    val insightsPrompt = "No README content available"
    val summaryPrompt = "Summary prompt"

    every { gitService.cloneRepository(repoUrl, branch) } returns clonedRepo
    every { codeAnalyzer.findReadmeFile(clonedRepo) } returns noReadme
    every { codeAnalyzer.analyzeStructure(clonedRepo) } returns emptyMap()
    every { codeAnalyzer.collectAllCodeSnippets(clonedRepo) } returns codeSnippets
    every { modelContextService.buildInsightsPrompt(noReadme) } returns insightsPrompt
    every { modelContextService.buildSummaryPrompt(any(), any()) } returns summaryPrompt
    coEvery { modelContextService.generateResponse(any()) } returns modelResponse

    // Act
    val result = repositoryAnalysisService.analyzeRepository(repoUrl, branch)

    // Assert
    assertEquals(
      """|$modelResponse
       |
       |$modelResponse
       |"""
        .trimMargin(),
      result,
    )
    verify { codeAnalyzer.findReadmeFile(clonedRepo) }
    verify {
      runBlocking {
        modelContextService.generateResponse(match { prompt -> prompt.contains("No README content available") })
      }
    }
  }
}
