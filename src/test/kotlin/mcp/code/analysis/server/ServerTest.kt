package mcp.code.analysis.server

import io.mockk.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server as SdkServer
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mcp.code.analysis.service.RepositoryAnalysisService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.Logger

class ServerTest {
  private lateinit var repositoryAnalysisService: RepositoryAnalysisService
  private lateinit var logger: Logger
  private lateinit var server: Server
  private lateinit var mockSdkServer: SdkServer

  @BeforeEach
  fun setUp() {
    repositoryAnalysisService = mockk(relaxed = true)
    logger = mockk(relaxed = true)
    mockSdkServer = mockk(relaxed = true)
    server = Server(repositoryAnalysisService, logger)
  }

  @Test
  fun `constructor should initialize with default values when not provided`() {
    // Arrange
    val defaultServer = Server()
    val implField = defaultServer.javaClass.getDeclaredField("implementation")
    implField.isAccessible = true

    // Act
    val implementation = implField.get(defaultServer) as Implementation

    // Assert
    assertEquals("MCP GitHub Code Analysis Server", implementation.name)
    assertEquals("0.1.0", implementation.version)
  }

  @Test
  @Disabled("Temporarily disabled")
  fun `configureServer should register analyze-repository tool`() {
    // Arrange
    mockkConstructor(SdkServer::class)
    val mockSdkServer = mockk<SdkServer>(relaxed = true)
    every { anyConstructed<SdkServer>() } returns mockSdkServer
    every { mockSdkServer.addTool(any(), any(), any(), any()) } returns mockk()

    val configureServerMethod = server.javaClass.getDeclaredMethod("configureServer")
    configureServerMethod.isAccessible = true
    configureServerMethod.invoke(server)

    // Act & Assert
    verify { mockSdkServer.addTool(any(), any(), any(), any()) }
  }

  @Test
  fun `analyze-repository tool should call repositoryAnalysisService`() = runBlocking {
    // Arrange
    val configureServerMethod = server.javaClass.getDeclaredMethod("configureServer")
    configureServerMethod.isAccessible = true

    val handlerCaptor = slot<suspend (CallToolRequest) -> CallToolResult>()

    mockkConstructor(SdkServer::class)
    every { anyConstructed<SdkServer>().addTool(any(), any(), any(), capture(handlerCaptor)) } just Runs

    configureServerMethod.invoke(server)

    val mockRequest = mockk<CallToolRequest>()
    every { mockRequest.arguments } returns
      JsonObject(
        mapOf("repoUrl" to JsonPrimitive("https://github.com/example/repo"), "branch" to JsonPrimitive("main"))
      )

    coEvery { repositoryAnalysisService.analyzeRepository("https://github.com/example/repo", "main") } returns
      "Analysis result"

    val result = handlerCaptor.captured(mockRequest)

    // Assert & Verify
    assertEquals(false, result.isError)
    assertEquals("Analysis result", (result.content.first() as TextContent).text)
    coVerify { repositoryAnalysisService.analyzeRepository("https://github.com/example/repo", "main") }
  }

  @Test
  @Disabled("Temporarily disabled")
  fun `runMcpServerUsingStdio should connect transport`() = runBlocking {
    // Arrange
    mockkConstructor(StdioServerTransport::class)
    val mockTransport = mockk<StdioServerTransport>(relaxed = true)
    every { anyConstructed<StdioServerTransport>() } returns mockTransport

    mockkConstructor(SdkServer::class)
    val mockSdkServer = mockk<SdkServer>(relaxed = true)
    every { anyConstructed<SdkServer>() } returns mockSdkServer
    coEvery { mockSdkServer.connect(any()) } just Runs
    coEvery { mockSdkServer.onClose(any()) } answers { firstArg<() -> Unit>().invoke() }

    // Act
    server.runMcpServerUsingStdio()

    // Assert
    coVerify { mockSdkServer.connect(mockTransport) }
  }
}
