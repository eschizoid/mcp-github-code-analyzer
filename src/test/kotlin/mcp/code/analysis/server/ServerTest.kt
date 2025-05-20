package mcp.code.analysis.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.mockk.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import io.modelcontextprotocol.kotlin.sdk.server.Server as SdkServer
import kotlin.invoke
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mcp.code.analysis.service.RepositoryAnalysisService
import org.junit.jupiter.api.BeforeEach
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
  fun `configureServer should register analyze-repository tool`() {
    // Arrange
    mockkConstructor(SdkServer::class)
    every { anyConstructed<SdkServer>().addTool(any(), any(), any(), any()) } returns mockk()

    val configureServerMethod = server.javaClass.getDeclaredMethod("configureServer")
    configureServerMethod.isAccessible = true
    configureServerMethod.invoke(server)

    // Assert
    verify { anyConstructed<SdkServer>().addTool(any(), any(), any(), any()) }
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
  fun `runMcpServerUsingStdio should connect transport`() = runBlocking {
    // Arrange
    val mockSdkServer = mockk<SdkServer>(relaxed = true)

    mockkConstructor(StdioServerTransport::class)

    coEvery { mockSdkServer.connect(any()) } just Runs
    coEvery { mockSdkServer.onClose(any()) } answers { firstArg<() -> Unit>().invoke() }

    // Act
    val testServer = TestableServer(repositoryAnalysisService, logger, mockSdkServer)
    testServer.runMcpServerUsingStdio()

    // Assert
    coVerify { mockSdkServer.connect(any()) }
  }

  @Test
  fun `runSseMcpServerWithPlainConfiguration should start server`() {
    // Arrange
    val mockSdkServer = mockk<SdkServer>(relaxed = true)
    val mockServer = mockk<EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>>(relaxed = true)

    mockkStatic("io.ktor.server.engine.EmbeddedServerKt")
    every { embeddedServer(CIO, host = "0.0.0.0", port = 3001, module = any()) } returns mockServer
    every { mockServer.start(wait = true) } returns mockServer

    coEvery { mockSdkServer.connect(any()) } just Runs
    coEvery { mockSdkServer.onClose(any()) } answers { firstArg<() -> Unit>().invoke() }

    val testServer = spyk(TestableServer(repositoryAnalysisService, logger, mockSdkServer), recordPrivateCalls = true)

    // Act
    testServer.runSseMcpServerWithPlainConfiguration(3001)

    // Assert
    verify { embeddedServer(CIO, host = "0.0.0.0", port = 3001, module = any()) }
  }

  @Test
  fun `runSseMcpServerUsingKtorPlugin should start server`() {
    // Arrange
    val mockSdkServer = mockk<SdkServer>(relaxed = true)
    val mockServer = mockk<EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>>(relaxed = true)

    mockkStatic("io.ktor.server.engine.EmbeddedServerKt")
    every { embeddedServer(CIO, host = "0.0.0.0", port = 3001, module = any()) } returns mockServer
    every { mockServer.start(wait = true) } returns mockServer

    coEvery { mockSdkServer.connect(any()) } just Runs
    coEvery { mockSdkServer.onClose(any()) } answers { firstArg<() -> Unit>().invoke() }

    val testServer = spyk(TestableServer(repositoryAnalysisService, logger, mockSdkServer), recordPrivateCalls = true)

    // Act
    testServer.runSseMcpServerUsingKtorPlugin(3001)

    // Assert
    verify { embeddedServer(CIO, host = "0.0.0.0", port = 3001, module = any()) }
  }

  class TestableServer(
    repositoryAnalysisService: RepositoryAnalysisService,
    logger: Logger,
    private val sdkServer: SdkServer,
  ) : Server(repositoryAnalysisService, logger) {
    override fun configureServer(): SdkServer = sdkServer

    override fun runSseMcpServerUsingKtorPlugin(port: Int) {
      embeddedServer(CIO, host = "0.0.0.0", port = port) { mcp { configureServer() } }.start(wait = false)
    }

    override fun runSseMcpServerWithPlainConfiguration(port: Int) {
      embeddedServer(CIO, host = "0.0.0.0", port = port) {
          install(SSE)
          routing {}
        }
        .start(wait = false)
    }
  }
}
