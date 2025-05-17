package mcp.code.analysis.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import io.modelcontextprotocol.kotlin.sdk.server.Server as SdkServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import mcp.code.analysis.service.RepositoryAnalysisService
import org.slf4j.LoggerFactory

/*
 * This Kotlin code defines a server for analyzing GitHub repositories using the Model Context Protocol (MCP).
 * It provides two main functionalities:
 * 1. Analyzing a GitHub repository to provide code insights and structure summary.
 * 2. Checking the status of a previously started repository analysis.
 */
class Server {

  private val logger = LoggerFactory.getLogger(this::class.java)
  private val analysisService = RepositoryAnalysisService()

  /**
   * Starts an MCP server using standard input/output (stdio) for communication.
   *
   * This method configures the server and connects it to the standard input and output streams. It
   * will handle listing prompts, tools, and resources automatically.
   *
   * @return Unit This method does not return a value.
   */
  fun runMcpServerUsingStdio() {
    // Note: The server will handle listing prompts, tools, and resources automatically.
    // The handleListResourceTemplates will return empty as defined in the Server code.
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
   * The url can be accessed in the MCP inspector at [http://localhost:$port]
   *
   * @param port The port number on which the SSE MCP server will listen for client connections.
   * @return Unit This method does not return a value.
   */
  fun runSseMcpServerWithPlainConfiguration(port: Int): Unit = runBlocking {
    val servers = ConcurrentMap<String, SdkServer>()
    logger.info("Starting sse server on port $port. ")
    logger.info("Use inspector to connect to the http://localhost:$port/sse")

    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        install(SSE)
        routing {
          sse("/sse") {
            val transport = SseServerTransport("/message", this)
            val server: SdkServer = configureServer()

            // For SSE, you can also add prompts/tools/resources if needed:
            // server.addTool(...), server.addPrompt(...), server.addResource(...)

            servers[transport.sessionId] = server

            server.onClose {
              logger.info("Server closed")
              servers.remove(transport.sessionId)
            }

            server.connect(transport)
          }
          post("/message") {
            logger.info("Received Message")
            val sessionId: String = call.request.queryParameters["sessionId"]!!
            val transport = servers[sessionId]?.transport as? SseServerTransport
            if (transport == null) {
              call.respond(HttpStatusCode.NotFound, "Session not found")
              return@post
            }

            transport.handlePostMessage(call)
          }
        }
      }
      .start(wait = true)
  }

  /**
   * Starts an SSE (Server Sent Events) MCP server using the Ktor framework and the specified port.
   *
   * The url can be accessed in the MCP inspector at [http://localhost:$port]
   *
   * @param port The port number on which the SSE MCP server will listen for client connections.
   * @return Unit This method does not return a value.
   */
  fun runSseMcpServerUsingKtorPlugin(port: Int): Unit = runBlocking {
    logger.info("Starting sse server on port $port")
    logger.info("Use inspector to connect to the http://localhost:$port/sse")

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
   * This method sets up the server with a tool for analyzing GitHub repositories and another for
   * checking the status of a previously started analysis. It defines the input schema and handles
   * the requests for each tool.
   *
   * @return The configured MCP server instance.
   */
  private fun configureServer(): SdkServer {
    val server =
      SdkServer(
        Implementation(name = "MCP GitHub Code Analysis Server", version = "0.1.0"),
        ServerOptions(
          capabilities =
            ServerCapabilities(
              prompts = ServerCapabilities.Prompts(listChanged = true),
              resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
              tools = ServerCapabilities.Tools(listChanged = true),
            )
        ),
      )

    server.addTool(
      name = "github-code-analyzer-start",
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
                      "description" to
                        JsonPrimitive("GitHub repository URL (e.g., https://github.com/owner/repo)"),
                    )
                  ),
                "branch" to
                  JsonObject(
                    mapOf(
                      "type" to JsonPrimitive("string"),
                      "description" to
                        JsonPrimitive("Branch to analyze (default: mcp.code.analysis.server.main)"),
                    )
                  ),
                "analysisType" to
                  JsonObject(
                    mapOf(
                      "type" to JsonPrimitive("string"),
                      "description" to
                        JsonPrimitive("Analysis type: 'quick', 'core', or 'comprehensive'"),
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
          arguments["repoUrl"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing repoUrl parameter")
        val branch = arguments["branch"]?.jsonPrimitive?.content ?: "mcp.code.analysis.server.main"
        val analysisType = arguments["analysisType"]?.jsonPrimitive?.content ?: "quick"
        val result = analysisService.analyzeRepository(repoUrl, branch, analysisType)

        CallToolResult(
          content =
            listOf(
              TextContent(
                "Repository analysis started with ID: ${result.id}\n" +
                  "Current status: ${result.status}\n" +
                  "You can check the status using the status-check tool with this ID."
              )
            )
        )
      } catch (e: Exception) {
        CallToolResult(
          content = listOf(TextContent("Error analyzing repository: ${e.message}")),
          isError = true,
        )
      }
    }

    server.addTool(
      name = "github-code-analyzer-status-check",
      description = "Check status of a previously started repository analysis",
      inputSchema =
        Tool.Input(
          properties =
            JsonObject(
              mapOf(
                "id" to
                  JsonObject(
                    mapOf(
                      "type" to JsonPrimitive("string"),
                      "description" to JsonPrimitive("Analysis ID to check"),
                    )
                  )
              )
            ),
          required = listOf("id"),
        ),
    ) { request ->
      try {
        val arguments = request.arguments
        val analysisId =
          arguments["id"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing analysis ID")

        val status = analysisService.getAnalysisStatus(analysisId)

        val responseContent = StringBuilder()
        responseContent.append("Analysis ID: ${status.id}\n")
        responseContent.append("Status: ${status.status}\n")

        if (status.status == "completed") {
          responseContent.append("\n## Summary\n${status.summary ?: "No summary available"}\n")

          if (!status.insights.isNullOrEmpty()) {
            responseContent.append("\n## Key Insights\n")
            status.insights.forEachIndexed { index, insight ->
              responseContent.append("${index + 1}. $insight\n")
            }
          }
        } else if (status.status == "failed") {
          responseContent.append("\nError: ${status.summary}")
        }

        CallToolResult(content = listOf(TextContent(responseContent.toString())))
      } catch (e: Exception) {
        CallToolResult(
          content = listOf(TextContent("Error checking status: ${e.message}")),
          isError = true,
        )
      }
    }

    return server
  }
}
