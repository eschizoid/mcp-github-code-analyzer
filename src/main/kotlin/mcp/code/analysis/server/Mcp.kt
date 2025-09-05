package mcp.code.analysis.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.cors.routing.*
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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
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
          tools = ServerCapabilities.Tools(listChanged = false),
          prompts = ServerCapabilities.Prompts(listChanged = false),
          resources = ServerCapabilities.Resources(subscribe = false, listChanged = false),
        )
    ),
) {

  private val asyncOperations = ConcurrentHashMap<String, Job>()
  private val operationResults = ConcurrentHashMap<String, String>()
  private val operationProgress = ConcurrentHashMap<String, String>()

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

        install(CORS) {
          anyHost()
          allowCredentials = true
          allowNonSimpleContentTypes = true
          allowMethod(HttpMethod.Options)
          allowMethod(HttpMethod.Post)
          allowMethod(HttpMethod.Get)
          allowHeader(HttpHeaders.ContentType)
          allowHeader(HttpHeaders.Accept)
          allowHeader(HttpHeaders.Authorization)
          allowHeader("X-Requested-With")
          allowHeader("Cache-Control")
        }

        routing {
          get("/") { call.respondText("MCP GitHub Code Analysis Server v0.1.0", ContentType.Text.Plain) }
          get("/health") { call.respondText("MCP Server is running", ContentType.Text.Plain) }

          sse("/sse") {
            logger.info("New SSE connection established from ${call.request.origin.remoteHost}")
            val transport = SseServerTransport("/message", this)
            val server = configureServer()
            servers[transport.sessionId] = server
            logger.info("Created server for session: ${transport.sessionId}")

            send(ServerSentEvent("connected", event = "connection"))

            val keepAliveJob = launch {
              while (isActive) {
                try {
                  send(ServerSentEvent("ping", event = "keepalive"))
                  delay(10_000)
                } catch (e: Exception) {
                  logger.debug("Keep-alive failed for session ${transport.sessionId}: ${e.message}")
                  break
                }
              }
            }

            server.onClose {
              logger.info("Server closed for session: ${transport.sessionId}")
              servers.remove(transport.sessionId)
              keepAliveJob.cancel()
              asyncOperations.keys.forEach { operationKey ->
                asyncOperations[operationKey]?.cancel()
                asyncOperations.remove(operationKey)
                operationProgress.remove(operationKey)
              }
            }

            try {
              logger.info("Attempting to connect server for session: ${transport.sessionId}")
              server.connect(transport)
              logger.info("Server successfully connected for session: ${transport.sessionId}")
              awaitCancellation()
            } catch (e: Exception) {
              logger.error("Connection error for session ${transport.sessionId}: ${e.message}", e)
              throw e
            } finally {
              keepAliveJob.cancel()
              servers.remove(transport.sessionId)
              logger.info("SSE connection closed for session: ${transport.sessionId}")
            }
          }

          post("/message") {
            try {
              withTimeout(15_000) {
                val sessionId =
                  call.request.queryParameters["sessionId"]
                    ?: return@withTimeout call.respond(HttpStatusCode.BadRequest, "Missing sessionId parameter")

                logger.debug("Handling message for session: $sessionId")

                val server = servers[sessionId]
                if (server == null) {
                  logger.warn("Session not found: $sessionId")
                  call.respond(HttpStatusCode.NotFound, "Session not found")
                  return@withTimeout
                }

                val transport = server.transport as? SseServerTransport
                if (transport == null) {
                  logger.warn("Invalid transport for session: $sessionId")
                  call.respond(HttpStatusCode.InternalServerError, "Invalid transport")
                  return@withTimeout
                }

                logger.debug("Processing message for session: $sessionId")
                transport.handlePostMessage(call)
                call.respond(HttpStatusCode.OK)
              }
            } catch (_: TimeoutCancellationException) {
              logger.error("Message handling timed out after 15 seconds")
              call.respond(HttpStatusCode.RequestTimeout, "Request processing timed out")
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
    logger.info("Starting SSE server using Ktor plugin on port $port")
    logger.info("Use inspector to connect to http://localhost:$port/sse")

    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        install(CORS) {
          anyHost()
          allowCredentials = true
          allowNonSimpleContentTypes = true
        }

        mcp {
          return@mcp configureServer()
        }
      }
      .start(wait = true)
  }

  /**
   * Configures the MCP server with tools, prompts, and resources for GitHub repository analysis.
   *
   * Additionally, configures prompts for codebase analysis and code review templates, plus resources for accessing
   * analysis results and repository metrics.
   *
   * @return The configured MCP server instance with all tools, prompts, and resources registered.
   */
  fun configureServer(): SdkServer {
    logger.info("Configuring MCP server with implementation: ${implementation.name} v${implementation.version}")
    val server = SdkServer(implementation, serverOptions)

    // Delegate registrations to dedicated registrars to keep this class small and cohesive
    registerTools(server, repositoryAnalysisService, logger, asyncOperations, operationResults, operationProgress)
    registerPrompts(server)
    registerResources(server)

    logger.info("MCP server configured successfully with 3 tools, 2 prompts, and 2 resources")
    return server
  }
}
