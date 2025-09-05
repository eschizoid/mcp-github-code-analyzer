package mcp.code.analysis.server

import io.mockk.*
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.Server as SdkServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mcp.code.analysis.service.RepositoryAnalysisService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class McpTest {
  private lateinit var repositoryAnalysisService: RepositoryAnalysisService
  private lateinit var serverUnderTest: Mcp

  private val toolHandlers = mutableMapOf<String, suspend (CallToolRequest) -> CallToolResult>()

  @BeforeEach
  fun setUp() {
    repositoryAnalysisService = mockk()
    serverUnderTest = Mcp(repositoryAnalysisService = repositoryAnalysisService)

    mockkConstructor(SdkServer::class)

    every {
      anyConstructed<SdkServer>()
        .addTool(
          name = capture(slot<String>()),
          description = any(),
          inputSchema = any(),
          handler = capture(slot<suspend (CallToolRequest) -> CallToolResult>()),
        )
    } answers
      {
        val toolName = firstArg<String>()
        val handler = lastArg<suspend (CallToolRequest) -> CallToolResult>()
        toolHandlers[toolName] = handler
      }

    every {
      anyConstructed<SdkServer>()
        .addPrompt(name = any<String>(), description = any(), arguments = any(), promptProvider = any())
    } returns Unit

    every {
      anyConstructed<SdkServer>()
        .addResource(name = any<String>(), description = any(), uri = any(), mimeType = any(), readHandler = any())
    } returns Unit
  }

  @AfterEach
  fun tearDown() {
    unmockkConstructor(SdkServer::class)
    toolHandlers.clear()
  }

  @Test
  fun `configureServer registers tool and handler processes success`() = runBlocking {
    // Arrange
    val repoUrl = "https://github.com/test/repo"
    val branch = "main"
    val expectedSummary = "Analysis successful"
    coEvery { repositoryAnalysisService.analyzeRepository(repoUrl, branch) } returns expectedSummary

    // Act
    serverUnderTest.configureServer()

    verify {
      anyConstructed<SdkServer>()
        .addTool(name = eq("analyze-repository"), description = any(), inputSchema = any(), handler = any())
    }

    val request =
      CallToolRequest(
        arguments = JsonObject(mapOf("repoUrl" to JsonPrimitive(repoUrl), "branch" to JsonPrimitive(branch))),
        name = "analyze-repository",
      )
    val result = toolHandlers["analyze-repository"]!!.invoke(request)

    // Assert
    assertFalse(result.isError == true, "Result should not be an error on success")
    assertEquals(1, result.content.size)
    val textContent = result.content.first() as? TextContent
    assertNotNull(textContent, "Content should be TextContent")
    assertEquals(expectedSummary, textContent?.text)
    coVerify { repositoryAnalysisService.analyzeRepository(repoUrl, branch) }
  }

  @Test
  fun `tool handler processes repository analysis error`() = runBlocking {
    // Arrange
    val repoUrl = "https://github.com/test/repo"
    val branch = "main"
    val errorMessage = "Failed to analyze due to network issue"

    coEvery { repositoryAnalysisService.analyzeRepository(repoUrl, branch) } throws Exception(errorMessage)

    // Act
    serverUnderTest.configureServer()

    val request =
      CallToolRequest(
        arguments = JsonObject(mapOf("repoUrl" to JsonPrimitive(repoUrl), "branch" to JsonPrimitive(branch))),
        name = "analyze-repository",
      )
    val result = toolHandlers["analyze-repository"]!!.invoke(request)

    // Assert
    assertTrue(result.isError == true, "Result should be an error")
    assertEquals(1, result.content.size)
    val textContent = result.content.first() as? TextContent
    assertNotNull(textContent, "Content should be TextContent")
    assertTrue(
      textContent?.text?.contains("Error analyzing repository: $errorMessage") == true,
      "Error message mismatch. Actual: ${textContent?.text}",
    )
    coVerify { repositoryAnalysisService.analyzeRepository(repoUrl, branch) }
  }

  @Test
  fun `tool handler processes missing repoUrl argument`() = runBlocking {
    // Act
    serverUnderTest.configureServer()

    val request =
      CallToolRequest(arguments = JsonObject(mapOf("branch" to JsonPrimitive("main"))), name = "analyze-repository")
    val result = toolHandlers["analyze-repository"]!!.invoke(request)

    // Assert
    assertTrue(result.isError == true, "Result should be an error for missing repoUrl")
    assertEquals(1, result.content.size)
    val textContent = result.content.first() as? TextContent
    assertNotNull(textContent, "Content should be TextContent")
    assertTrue(
      textContent?.text?.contains("Error analyzing repository: Missing repoUrl parameter") == true,
      "Error message for missing repoUrl mismatch. Actual: ${textContent?.text}",
    )
    coVerify(exactly = 0) { repositoryAnalysisService.analyzeRepository(any(), any()) }
  }

  @Test
  fun `tool handler uses default branch if not provided`() = runBlocking {
    // Arrange
    val repoUrl = "https://github.com/test/repo"
    val defaultBranch = "main"
    val expectedSummary = "Analysis with default branch successful"
    coEvery { repositoryAnalysisService.analyzeRepository(repoUrl, defaultBranch) } returns expectedSummary

    // Act
    serverUnderTest.configureServer()

    val request =
      CallToolRequest(arguments = JsonObject(mapOf("repoUrl" to JsonPrimitive(repoUrl))), name = "analyze-repository")
    val result = toolHandlers["analyze-repository"]!!.invoke(request)

    // Assert
    assertFalse(result.isError == true, "Result should not be an error when using default branch")
    assertEquals(1, result.content.size)
    val textContent = result.content.first() as? TextContent
    assertNotNull(textContent, "Content should be TextContent")
    assertEquals(expectedSummary, textContent?.text)
    coVerify { repositoryAnalysisService.analyzeRepository(repoUrl, defaultBranch) }
  }

  @Test
  fun `check-analysis-status tool handler returns completed analysis result`() = runBlocking {
    // Arrange
    val repoUrl = "https://github.com/test/repo"
    val branch = "main"
    val analysisResult = "Analysis completed successfully"
    coEvery { repositoryAnalysisService.analyzeRepository(repoUrl, branch) } returns analysisResult

    // Act
    serverUnderTest.configureServer()

    // First analyze repository to cache result
    val analyzeRequest =
      CallToolRequest(
        arguments = JsonObject(mapOf("repoUrl" to JsonPrimitive(repoUrl), "branch" to JsonPrimitive(branch))),
        name = "analyze-repository",
      )
    toolHandlers["analyze-repository"]!!.invoke(analyzeRequest)

    // Then check status
    val statusRequest =
      CallToolRequest(
        arguments = JsonObject(mapOf("repoUrl" to JsonPrimitive(repoUrl), "branch" to JsonPrimitive(branch))),
        name = "check-analysis-status",
      )
    val result = toolHandlers["check-analysis-status"]!!.invoke(statusRequest)

    // Assert
    assertFalse(result.isError == true, "Result should not be an error")
    assertEquals(1, result.content.size)
    val textContent = result.content.first() as? TextContent
    assertNotNull(textContent, "Content should be TextContent")
    assertTrue(
      textContent?.text?.contains("Analysis completed successfully") == true,
      "Should contain completion message. Actual: ${textContent?.text}",
    )
  }

  @Test
  fun `check-analysis-status tool handler returns no analysis found`() = runBlocking {
    // Arrange
    val repoUrl = "https://github.com/test/repo"
    val branch = "main"

    // Act
    serverUnderTest.configureServer()

    val request =
      CallToolRequest(
        arguments = JsonObject(mapOf("repoUrl" to JsonPrimitive(repoUrl), "branch" to JsonPrimitive(branch))),
        name = "check-analysis-status",
      )
    val result = toolHandlers["check-analysis-status"]!!.invoke(request)

    // Assert
    assertFalse(result.isError == true, "Result should not be an error")
    assertEquals(1, result.content.size)
    val textContent = result.content.first() as? TextContent
    assertNotNull(textContent, "Content should be TextContent")
    assertTrue(
      textContent?.text?.contains("No analysis found for this repository") == true,
      "Should contain no analysis message. Actual: ${textContent?.text}",
    )
  }

  @Test
  fun `check-analysis-status tool handler uses default branch if not provided`() = runBlocking {
    // Arrange
    val repoUrl = "https://github.com/test/repo"

    // Act
    serverUnderTest.configureServer()

    val request =
      CallToolRequest(
        arguments = JsonObject(mapOf("repoUrl" to JsonPrimitive(repoUrl))),
        name = "check-analysis-status",
      )
    val result = toolHandlers["check-analysis-status"]!!.invoke(request)

    // Assert
    assertFalse(result.isError == true, "Result should not be an error")
    assertEquals(1, result.content.size)
    val textContent = result.content.first() as? TextContent
    assertNotNull(textContent, "Content should be TextContent")
    assertTrue(
      textContent?.text?.contains("No analysis found for this repository") == true,
      "Should handle default branch correctly. Actual: ${textContent?.text}",
    )
  }

  @Test
  fun `check-analysis-status tool handler processes missing repoUrl argument`() = runBlocking {
    // Act
    serverUnderTest.configureServer()

    val request =
      CallToolRequest(arguments = JsonObject(mapOf("branch" to JsonPrimitive("main"))), name = "check-analysis-status")
    val result = toolHandlers["check-analysis-status"]!!.invoke(request)

    // Assert
    assertTrue(result.isError == true, "Result should be an error for missing repoUrl")
    assertEquals(1, result.content.size)
    val textContent = result.content.first() as? TextContent
    assertNotNull(textContent, "Content should be TextContent")
    assertTrue(
      textContent?.text?.contains("Error checking status: Missing repoUrl parameter") == true,
      "Error message for missing repoUrl mismatch. Actual: ${textContent?.text}",
    )
  }

  @Test
  fun `cancel-analysis tool handler cancels running operation`() = runBlocking {
    // Arrange
    val repoUrl = "https://github.com/test/repo"
    val branch = "main"

    // Act
    serverUnderTest.configureServer()

    val request =
      CallToolRequest(
        arguments = JsonObject(mapOf("repoUrl" to JsonPrimitive(repoUrl), "branch" to JsonPrimitive(branch))),
        name = "cancel-analysis",
      )
    val result = toolHandlers["cancel-analysis"]!!.invoke(request)

    // Assert
    assertFalse(result.isError == true, "Result should not be an error")
    assertEquals(1, result.content.size)
    val textContent = result.content.first() as? TextContent
    assertNotNull(textContent, "Content should be TextContent")
    assertTrue(
      textContent?.text?.contains("No running analysis found") == true,
      "Should indicate no running analysis. Actual: ${textContent?.text}",
    )
  }

  @Test
  fun `cancel-analysis tool handler with clearCache clears cached results`() = runBlocking {
    // Arrange
    val repoUrl = "https://github.com/test/repo"
    val branch = "main"
    val analysisResult = "Analysis completed"
    coEvery { repositoryAnalysisService.analyzeRepository(repoUrl, branch) } returns analysisResult

    // Act
    serverUnderTest.configureServer()

    // First analyze repository to cache result
    val analyzeRequest =
      CallToolRequest(
        arguments = JsonObject(mapOf("repoUrl" to JsonPrimitive(repoUrl), "branch" to JsonPrimitive(branch))),
        name = "analyze-repository",
      )
    toolHandlers["analyze-repository"]!!.invoke(analyzeRequest)

    // Then cancel with clearCache=true
    val cancelRequest =
      CallToolRequest(
        arguments =
          JsonObject(
            mapOf(
              "repoUrl" to JsonPrimitive(repoUrl),
              "branch" to JsonPrimitive(branch),
              "clearCache" to JsonPrimitive("true"),
            )
          ),
        name = "cancel-analysis",
      )
    val result = toolHandlers["cancel-analysis"]!!.invoke(cancelRequest)

    // Assert
    assertFalse(result.isError == true, "Result should not be an error")
    assertEquals(1, result.content.size)
    val textContent = result.content.first() as? TextContent
    assertNotNull(textContent, "Content should be TextContent")
    assertTrue(
      textContent?.text?.contains("cleared cached results") == true,
      "Should indicate cache was cleared. Actual: ${textContent?.text}",
    )
  }

  @Test
  fun `cancel-analysis tool handler uses default branch if not provided`() = runBlocking {
    // Arrange
    val repoUrl = "https://github.com/test/repo"

    // Act
    serverUnderTest.configureServer()

    val request =
      CallToolRequest(arguments = JsonObject(mapOf("repoUrl" to JsonPrimitive(repoUrl))), name = "cancel-analysis")
    val result = toolHandlers["cancel-analysis"]!!.invoke(request)

    // Assert
    assertFalse(result.isError == true, "Result should not be an error")
    assertEquals(1, result.content.size)
    val textContent = result.content.first() as? TextContent
    assertNotNull(textContent, "Content should be TextContent")
    assertTrue(
      textContent?.text?.contains("No running analysis found") == true,
      "Should handle default branch correctly. Actual: ${textContent?.text}",
    )
  }

  @Test
  fun `cancel-analysis tool handler processes missing repoUrl argument`() = runBlocking {
    // Act
    serverUnderTest.configureServer()

    val request =
      CallToolRequest(arguments = JsonObject(mapOf("branch" to JsonPrimitive("main"))), name = "cancel-analysis")
    val result = toolHandlers["cancel-analysis"]!!.invoke(request)

    // Assert
    assertTrue(result.isError == true, "Result should be an error for missing repoUrl")
    assertEquals(1, result.content.size)
    val textContent = result.content.first() as? TextContent
    assertNotNull(textContent, "Content should be TextContent")
    assertTrue(
      textContent?.text?.contains("Error cancelling analysis: Missing repoUrl parameter") == true,
      "Error message for missing repoUrl mismatch. Actual: ${textContent?.text}",
    )
  }
}
