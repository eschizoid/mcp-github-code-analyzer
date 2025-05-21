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

class ServerTest {
  private lateinit var repositoryAnalysisService: RepositoryAnalysisService
  private lateinit var serverUnderTest: Server

  private val toolHandlerSlot = slot<suspend (CallToolRequest) -> CallToolResult>()

  @BeforeEach
  fun setUp() {
    repositoryAnalysisService = mockk()
    serverUnderTest = Server(repositoryAnalysisService = repositoryAnalysisService)

    mockkConstructor(SdkServer::class)

    every {
      anyConstructed<SdkServer>()
        .addTool(name = any(), description = any(), inputSchema = any(), handler = capture(toolHandlerSlot))
    } returns Unit // Assuming addTool returns Unit or the mock will handle it
  }

  @AfterEach
  fun tearDown() {
    unmockkConstructor(SdkServer::class)
  }

  @Test
  fun `configureServer registers tool and handler processes success`() = runBlocking {
    val repoUrl = "https://github.com/test/repo"
    val branch = "main"
    val expectedSummary = "Analysis successful"

    coEvery { repositoryAnalysisService.analyzeRepository(repoUrl, branch) } returns expectedSummary

    // Act: Call configureServer to trigger SdkServer instantiation and addTool
    serverUnderTest.configureServer()

    // Verify that addTool was called on the (mocked) SdkServer with the correct tool name
    verify {
      anyConstructed<SdkServer>()
        .addTool(
          name = eq("analyze-repository"),
          description = any(), // Can be more specific if needed
          inputSchema = any(), // Can be more specific by creating the expected Tool.Input
          handler = toolHandlerSlot.captured,
        )
    }

    // Prepare a request for the captured handler
    val request =
      CallToolRequest(
        arguments = JsonObject(mapOf("repoUrl" to JsonPrimitive(repoUrl), "branch" to JsonPrimitive(branch))),
        name = "analyze-repository",
      )

    // Invoke the captured handler directly
    val result = toolHandlerSlot.captured.invoke(request)

    // Assert the handler's output
    assertFalse(result.isError == true, "Result should not be an error on success")
    assertEquals(1, result.content.size)
    val textContent = result.content.first() as? TextContent
    assertNotNull(textContent, "Content should be TextContent")
    assertEquals(expectedSummary, textContent?.text)

    // Verify the service was called correctly
    coVerify { repositoryAnalysisService.analyzeRepository(repoUrl, branch) }
  }

  @Test
  fun `tool handler processes repository analysis error`() = runBlocking {
    val repoUrl = "https://github.com/test/repo"
    val branch = "main"
    val errorMessage = "Failed to analyze due to network issue"

    coEvery { repositoryAnalysisService.analyzeRepository(repoUrl, branch) } throws Exception(errorMessage)

    serverUnderTest.configureServer() // This captures the handler

    val request =
      CallToolRequest(
        arguments = JsonObject(mapOf("repoUrl" to JsonPrimitive(repoUrl), "branch" to JsonPrimitive(branch))),
        name = "analyze-repository",
      )
    val result = toolHandlerSlot.captured.invoke(request)

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
    serverUnderTest.configureServer() // This captures the handler

    val request =
      CallToolRequest(
        arguments =
          JsonObject(
            mapOf(
              // "repoUrl" is intentionally missing
              "branch" to JsonPrimitive("main")
            )
          ),
        name = "analyze-repository",
      )
    val result = toolHandlerSlot.captured.invoke(request)

    assertTrue(result.isError == true, "Result should be an error for missing repoUrl")
    assertEquals(1, result.content.size)
    val textContent = result.content.first() as? TextContent
    assertNotNull(textContent, "Content should be TextContent")
    assertTrue(
      textContent?.text?.contains("Error analyzing repository: Missing repoUrl parameter") == true,
      "Error message for missing repoUrl mismatch. Actual: ${textContent?.text}",
    )

    // Ensure the analysis service was not called
    coVerify(exactly = 0) { repositoryAnalysisService.analyzeRepository(any(), any()) }
  }

  @Test
  fun `tool handler uses default branch if not provided`() = runBlocking {
    val repoUrl = "https://github.com/test/repo"
    val defaultBranch = "main" // As defined in the handler logic
    val expectedSummary = "Analysis with default branch successful"

    // Expect the service to be called with the default branch
    coEvery { repositoryAnalysisService.analyzeRepository(repoUrl, defaultBranch) } returns expectedSummary

    serverUnderTest.configureServer() // This captures the handler

    val request =
      CallToolRequest(
        arguments =
          JsonObject(
            mapOf(
              "repoUrl" to JsonPrimitive(repoUrl)
              // "branch" is intentionally missing
            )
          ),
        name = "analyze-repository",
      )
    val result = toolHandlerSlot.captured.invoke(request)

    assertFalse(result.isError == true, "Result should not be an error when using default branch")
    assertEquals(1, result.content.size)
    val textContent = result.content.first() as? TextContent
    assertNotNull(textContent, "Content should be TextContent")
    assertEquals(expectedSummary, textContent?.text)
    coVerify { repositoryAnalysisService.analyzeRepository(repoUrl, defaultBranch) }
  }
}
