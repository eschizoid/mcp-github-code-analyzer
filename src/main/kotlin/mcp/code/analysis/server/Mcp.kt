package mcp.code.analysis.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server as SdkServer
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlin.collections.get
import kotlin.collections.remove
import kotlin.text.get
import kotlin.text.set
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import mcp.code.analysis.service.RepositoryAnalysisService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Server for analyzing GitHub repositories using the Model Context Protocol (MCP). Provides functionalities for
 * analyzing GitHub repositories and checking analysis status.
 */
class Mcp(
  private val repositoryAnalysisService: RepositoryAnalysisService = RepositoryAnalysisService(),
  private val logger: Logger = LoggerFactory.getLogger(Mcp::class.java),
  private val implementation: Implementation =
    Implementation(name = "MCP GitHub Code Analysis Server", version = "0.1.0"),
  private val serverOptions: ServerOptions =
    ServerOptions(
      capabilities =
        ServerCapabilities(
          prompts = ServerCapabilities.Prompts(listChanged = true),
          resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
          tools = ServerCapabilities.Tools(listChanged = true),
        )
    ),
) {

  /** Starts an MCP server using standard input/output (stdio) for communication. */
  fun runUsingStdio() {
    val server = configureServer()
    val transport =
      StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered(),
      )

    runBlocking {
      server.connect(transport)
      val done = Job()
      server.onClose { done.complete() }
      done.join()
      logger.info("Server closed")
    }
  }

  /**
   * Starts an SSE (Server Sent Events) MCP server using plain configuration and the specified port.
   *
   * @param port The port number on which the SSE MCP server will listen for client connections.
   */
  fun runSseWithPlainConfiguration(port: Int): Unit = runBlocking {
    val servers = ConcurrentMap<String, SdkServer>()
    logger.info("Starting SSE server on port $port.")
    logger.info("Use inspector to connect to the http://localhost:$port/sse")

    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        install(SSE)

        routing {
          sse("/sse") {
            val transport = SseServerTransport("/message", this)
            val server = configureServer()
            servers[transport.sessionId] = server

            server.onClose {
              logger.info("Server closed for session: ${transport.sessionId}")
              servers.remove(transport.sessionId)
            }

            try {
              server.connect(transport)
              logger.info("Server connected for session: ${transport.sessionId}")
              awaitCancellation()
            } catch (e: Exception) {
              logger.error("Connection error: ${e.message}", e)
            } finally {
              servers.remove(transport.sessionId)
              logger.info("SSE connection closed for session: ${transport.sessionId}")
            }
          }

          post("/message") {
            try {
              val sessionId =
                call.request.queryParameters["sessionId"]
                  ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing sessionId parameter")

              val server = servers[sessionId]
              if (server == null) {
                logger.warn("Session not found: $sessionId")
                call.respond(HttpStatusCode.NotFound, "Session not found")
                return@post
              }

              val transport = server.transport as? SseServerTransport
              if (transport == null) {
                logger.warn("Invalid transport for session: $sessionId")
                call.respond(HttpStatusCode.InternalServerError, "Invalid transport")
                return@post
              }

              logger.debug("Handling message for session: $sessionId")
              withTimeout(3_600_000) { transport.handlePostMessage(call) }

              call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
              logger.error("Error handling message: ${e.message}", e)
              call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
          }
        }
      }
      .start(wait = true)
  }

  /**
   * Starts an SSE (Server Sent Events) MCP server using the Ktor framework and the specified port.
   *
   * @param port The port number on which the SSE MCP server will listen for client connections.
   */
  fun runSseUsingKtorPlugin(port: Int): Unit = runBlocking {
    logger.debug("Starting SSE server on port $port")
    logger.debug("Use inspector to connect to http://localhost:$port/sse")

    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        mcp {
          return@mcp configureServer()
        }
      }
      .start(wait = true)
  }

  /**
   * Configures the MCP server with tools and their respective functionalities.
   *
   * @return The configured MCP server instance.
   */
  fun configureServer(): SdkServer {
    val server = SdkServer(implementation, serverOptions)

    server.addTool(
      name = "analyze-repository",
      description = "Analyzes GitHub repositories to provide code insights and structure summary",
      inputSchema =
        Tool.Input(
          properties =
            JsonObject(
              mapOf(
                "repoUrl" to
                  JsonObject(
                    mapOf(
                      "type" to JsonPrimitive("string"),
                      "description" to JsonPrimitive("GitHub repository URL (e.g., https://github.com/owner/repo)"),
                    )
                  ),
                "branch" to
                  JsonObject(
                    mapOf(
                      "type" to JsonPrimitive("string"),
                      "description" to JsonPrimitive("Branch to analyze (default: main)"),
                    )
                  ),
              )
            ),
          required = listOf("repoUrl"),
        ),
    ) { request ->
      try {
        val arguments = request.arguments
        val repoUrl =
          arguments["repoUrl"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing repoUrl parameter")
        val branch = arguments["branch"]?.jsonPrimitive?.content ?: "main"
        val result = repositoryAnalysisService.analyzeRepository(repoUrl, branch)
        CallToolResult(content = listOf(TextContent(result)))
      } catch (e: Exception) {
        CallToolResult(content = listOf(TextContent("Error analyzing repository: ${e.message}")), isError = true)
      }
    }
    return server
  }
}
