package mcp.code.analysis.service

import io.mockk.*
import java.io.File
import mcp.code.analysis.config.AppConfig
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.CloneCommand
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GitServiceTest {
  @TempDir lateinit var tempDir: File

  private lateinit var mockConfig: AppConfig
  private lateinit var gitService: GitService
  private lateinit var mockCloneCommand: CloneCommand
  private lateinit var mockGit: Git
  private lateinit var mockCheckoutCommand: CheckoutCommand

  @BeforeEach
  fun setUp() {
    mockConfig = mockk<AppConfig>()
    every { mockConfig.cloneDirectory } returns tempDir.absolutePath

    mockCloneCommand = mockk<CloneCommand>()
    mockGit = mockk<Git>()
    mockCheckoutCommand = mockk<CheckoutCommand>()

    // Mock static Git.cloneRepository method
    mockkStatic(Git::class)
    every { Git.cloneRepository() } returns mockCloneCommand

    // Mock the chained method calls on CloneCommand
    every { mockCloneCommand.setURI(any()) } returns mockCloneCommand
    every { mockCloneCommand.setDirectory(any()) } returns mockCloneCommand
    every { mockCloneCommand.setBranch(any()) } returns mockCloneCommand
    every { mockCloneCommand.call() } returns mockGit

    // Mock Git.checkout().setName().call() chain
    every { mockGit.checkout() } returns mockCheckoutCommand
    every { mockCheckoutCommand.setName(any()) } returns mockCheckoutCommand
    every { mockCheckoutCommand.call() } returns mockk()

    // Mock Git.close() method
    every { mockGit.close() } just runs

    gitService = GitService(mockConfig)
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `cloneRepository should successfully clone a repository`() {
    // Arrange
    val repoUrl = "https://github.com/testuser/testrepo"
    val branch = "main"

    // Act
    val result = gitService.cloneRepository(repoUrl, branch)

    // Assert
    assertTrue(result.isDirectory)
    assertTrue(result.name.contains("testrepo"))
    verify { mockCloneCommand.setURI(repoUrl) }
    verify { mockCloneCommand.setBranch(branch) }
    verify { mockCheckoutCommand.setName(branch) }
  }

  @Test
  fun `cloneRepository should handle errors properly`() {
    // Arrange
    val repoUrl = "https://github.com/user/repo.git"
    val branch = "main"
    val exception = RuntimeException("Clone failed")

    every { mockCloneCommand.call() } throws exception

    // Act & Assert
    val thrownException = assertThrows(RuntimeException::class.java) { gitService.cloneRepository(repoUrl, branch) }
    assertEquals("Clone failed", thrownException.message)
    verify { Git.cloneRepository() }
    verify { mockCloneCommand.setURI(repoUrl) }
    verify { mockCloneCommand.setBranch(branch) }
  }

  @Test
  fun `extract repo name should work correctly`() {
    // Arrange
    val extractRepoName = GitService::class.java.getDeclaredMethod("extractRepoName", String::class.java)
    extractRepoName.isAccessible = true
    val testCases =
      mapOf(
        "https://github.com/user/repo" to "repo",
        "https://github.com/user/repo.git" to "repo",
        "git@github.com:user/repo.git" to "repo",
        "https://github.com/organization/project-name" to "project-name",
      )

    // Act & Assert
    testCases.forEach { (input, expected) ->
      val actual = extractRepoName.invoke(gitService, input) as String
      assertEquals(expected, actual, "Failed to extract correct repo name from $input")
    }
  }
}
