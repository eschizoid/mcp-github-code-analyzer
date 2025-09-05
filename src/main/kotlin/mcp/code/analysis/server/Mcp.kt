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
import java.util.Locale.getDefault
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
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
   * Sets up three main tools:
   * - **analyze-repository**: Analyzes GitHub repositories to provide comprehensive code insights and structure
   *   summary. Supports any public GitHub repository URL, branch-specific analysis (defaults to 'main'), automatic
   *   caching to prevent duplicate analysis, synchronous analysis for quick responses (up to 20s timeout), and
   *   background processing for large repositories with progress tracking.
   * - **check-analysis-status**: Monitors the progress and completion status of repository analysis operations.
   *   Provides real-time progress tracking for background analyses, status reporting (Running, Completed, Cancelled,
   *   Failed), retrieval of completed analysis results, and error reporting for failed operations.
   * - **cancel-analysis**: Cancels running repository analysis operations with optional cache management. Offers
   *   immediate cancellation of background analysis jobs, optional cache clearing to remove stored results, detailed
   *   feedback on what actions were performed, and backward compatibility with existing usage patterns.
   *
   * Additionally, configures prompts for codebase analysis and code review templates, plus resources for accessing
   * analysis results and repository metrics.
   *
   * @return The configured MCP server instance with all tools, prompts, and resources registered.
   */
  fun configureServer(): SdkServer {
    logger.info("Configuring MCP server with implementation: ${implementation.name} v${implementation.version}")
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

        val operationKey = "$repoUrl:$branch"

        operationResults[operationKey]?.let { result ->
          return@addTool CallToolResult(content = listOf(TextContent("Cached result: $result")))
        }

        asyncOperations[operationKey]?.let { job ->
          if (job.isActive) {
            val progress = operationProgress[operationKey] ?: "Analysis in progress..."
            return@addTool CallToolResult(
              content =
                listOf(
                  TextContent(
                    """
                      Analysis already in progress for: $repoUrl (branch: $branch).
                      Current progress: $progress
                      Use 'check-analysis-status' to monitor progress.
                      """
                      .trimIndent()
                  )
                )
            )
          } else {
            asyncOperations.remove(operationKey)
          }
        }

        try {
          val startTime = System.currentTimeMillis()
          logger.info("Starting repository analysis for: $repoUrl")

          val result = withTimeout(20_000) { repositoryAnalysisService.analyzeRepository(repoUrl, branch) }

          val duration = System.currentTimeMillis() - startTime
          logger.info("Analysis completed in ${duration}ms")

          operationResults[operationKey] = result
          CallToolResult(content = listOf(TextContent(result)))
        } catch (_: TimeoutCancellationException) {
          val asyncJob =
            CoroutineScope(Dispatchers.IO).launch {
              try {
                logger.info("Starting background analysis for: $repoUrl")
                operationProgress[operationKey] = "Starting analysis..."
                operationProgress[operationKey] = "Processing files and dependencies..."
                val result = repositoryAnalysisService.analyzeRepository(repoUrl, branch)

                operationResults[operationKey] = result
                operationProgress[operationKey] = "Analysis completed successfully"

                logger.info("Background analysis completed for: $repoUrl")
              } catch (e: Exception) {
                logger.error("Background analysis failed for $repoUrl: ${e.message}", e)
                operationProgress[operationKey] = "Analysis failed: ${e.message}"
              } finally {
                asyncOperations.remove(operationKey)
              }
            }

          asyncOperations[operationKey] = asyncJob

          CallToolResult(
            content =
              listOf(
                TextContent(
                  """
                  Repository analysis started in the background for: $repoUrl (branch: $branch).
                  This may take several minutes for large repositories.
                  Use 'check-analysis-status' tool to monitor progress.
                  """
                    .trimIndent()
                )
              ),
            isError = false,
          )
        }
      } catch (e: Exception) {
        logger.error("Analysis failed: ${e.message}", e)
        CallToolResult(content = listOf(TextContent("Error analyzing repository: ${e.message}")), isError = true)
      }
    }

    server.addTool(
      name = "check-analysis-status",
      description = "Check the status of a repository analysis operation",
      inputSchema =
        Tool.Input(
          properties =
            JsonObject(
              mapOf(
                "repoUrl" to
                  JsonObject(
                    mapOf("type" to JsonPrimitive("string"), "description" to JsonPrimitive("GitHub repository URL"))
                  ),
                "branch" to
                  JsonObject(
                    mapOf(
                      "type" to JsonPrimitive("string"),
                      "description" to JsonPrimitive("Branch to check (default: main)"),
                    )
                  ),
              )
            ),
          required = listOf("repoUrl"),
        ),
    ) { request ->
      try {
        val repoUrl =
          request.arguments["repoUrl"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing repoUrl parameter")
        val branch = request.arguments["branch"]?.jsonPrimitive?.content ?: "main"
        val operationKey = "$repoUrl:$branch"

        when {
          operationResults.containsKey(operationKey) -> {
            val result = operationResults[operationKey]!!
            CallToolResult(content = listOf(TextContent("Analysis completed successfully:\n\n$result")))
          }
          asyncOperations.containsKey(operationKey) -> {
            val progress = operationProgress[operationKey] ?: "Analysis in progress..."
            val job = asyncOperations[operationKey]!!
            val status =
              when {
                job.isCompleted -> "Completed"
                job.isCancelled -> "Cancelled"
                else -> "Running"
              }
            CallToolResult(content = listOf(TextContent("Analysis status: $status\nProgress: $progress")))
          }
          operationProgress.containsKey(operationKey) -> {
            val progress = operationProgress[operationKey]!!
            if (progress.startsWith("Analysis failed:")) {
              CallToolResult(
                content = listOf(TextContent("Analysis failed: ${progress.substring(16)}")),
                isError = true,
              )
            } else {
              CallToolResult(content = listOf(TextContent("Final status: $progress")))
            }
          }
          else -> {
            CallToolResult(
              content = listOf(TextContent("No analysis found for this repository. Run 'analyze-repository' first."))
            )
          }
        }
      } catch (e: Exception) {
        CallToolResult(content = listOf(TextContent("Error checking status: ${e.message}")), isError = true)
      }
    }

    server.addTool(
      name = "cancel-analysis",
      description = "Cancel a running repository analysis operation with optional cache clearing",
      inputSchema =
        Tool.Input(
          properties =
            JsonObject(
              mapOf(
                "repoUrl" to
                  JsonObject(
                    mapOf("type" to JsonPrimitive("string"), "description" to JsonPrimitive("GitHub repository URL"))
                  ),
                "branch" to
                  JsonObject(
                    mapOf(
                      "type" to JsonPrimitive("string"),
                      "description" to JsonPrimitive("Branch to cancel (default: main)"),
                    )
                  ),
                "clearCache" to
                  JsonObject(
                    mapOf(
                      "type" to JsonPrimitive("boolean"),
                      "description" to JsonPrimitive("Whether to clear cached results (default: false)"),
                    )
                  ),
              )
            ),
          required = listOf("repoUrl"),
        ),
    ) { request ->
      try {
        val repoUrl =
          request.arguments["repoUrl"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing repoUrl parameter")
        val branch = request.arguments["branch"]?.jsonPrimitive?.content ?: "main"
        val clearCache = request.arguments["clearCache"]?.jsonPrimitive?.content?.toBoolean() ?: false
        val operationKey = "$repoUrl:$branch"

        val hadRunningOperation = asyncOperations[operationKey] != null
        val hadCachedResults = operationResults.containsKey(operationKey)

        asyncOperations[operationKey]?.cancel()
        asyncOperations.remove(operationKey)

        if (clearCache) {
          operationResults.remove(operationKey)
        }

        operationProgress[operationKey] = "Analysis cancelled by user"

        val repoInfo = "$repoUrl (branch: $branch)"
        val message =
          when {
            hadRunningOperation && clearCache -> {
              val cacheMsg = if (hadCachedResults) " and cached results cleared" else " and cache cleared"
              "Analysis cancelled$cacheMsg for repository: $repoInfo"
            }
            hadRunningOperation -> "Analysis cancelled for repository: $repoInfo. Cached results preserved."
            clearCache && hadCachedResults ->
              "No running analysis found, but cleared cached results for repository: $repoInfo"
            clearCache -> "No running analysis or cached results found for repository: $repoInfo"
            else -> "No running analysis found for this repository."
          }

        CallToolResult(content = listOf(TextContent(message)))
      } catch (e: Exception) {
        CallToolResult(content = listOf(TextContent("Error cancelling analysis: ${e.message}")), isError = true)
      }
    }

    server.addPrompt(
      name = "analyze-codebase",
      description = "Generate a comprehensive analysis prompt for a codebase",
      arguments =
        listOf(
          PromptArgument(
            name = "focus",
            description = "What aspect to focus on (architecture, security, performance, etc.)",
            required = false,
          ),
          PromptArgument(
            name = "language",
            description = "Primary programming language of the codebase",
            required = false,
          ),
        ),
    ) { request ->
      val focus = request.arguments?.get("focus") ?: "general architecture"
      val language = request.arguments?.get("language") ?: "any language"

      val promptText =
        """
        Please analyze this codebase with a focus on ${focus}.

        Primary language: $language

        Please provide:
        1. Overall architecture and design patterns
        2. Code quality and maintainability assessment
        3. Potential security concerns
        4. Performance considerations
        5. Recommendations for improvements

        Focus particularly on $focus aspects of the code.
      """
          .trimIndent()

      GetPromptResult(
        description = "Codebase analysis prompt focusing on $focus",
        messages = listOf(PromptMessage(role = Role.user, content = TextContent(promptText))),
      )
    }

    server.addPrompt(
      name = "code-review",
      description = "Generate a code review prompt template",
      arguments =
        listOf(
          PromptArgument(
            name = "type",
            description = "Type of review (security, performance, style, etc.)",
            required = false,
          )
        ),
    ) { request ->
      val reviewType = request.arguments?.get("type") ?: "comprehensive"

      val promptText =
        """
        Please perform a $reviewType code review of the following code.

        Review criteria:
        - Code clarity and readability
        - Best practices adherence
        - Potential bugs or issues
        - Performance implications
        - Security considerations
        - Maintainability

        Please provide specific feedback with examples and suggestions for improvement.
      """
          .trimIndent()

      GetPromptResult(
        description =
          "${reviewType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }} code review template",
        messages = listOf(PromptMessage(role = Role.user, content = TextContent(promptText))),
      )
    }

    server.addResource(
      uri = "repo://analysis-results",
      name = "Repository Analysis Results",
      description = "Latest repository analysis results",
      mimeType = "application/json",
    ) {
      // Return cached analysis results or a placeholder
      ReadResourceResult(
        contents =
          listOf(
            TextResourceContents(
              uri = "repo://analysis-results",
              mimeType = "application/json",
              text = """{"message": "No analysis results available yet. Run analyze-repository tool first."}""",
            )
          )
      )
    }

    server.addResource(
      uri = "repo://metrics",
      name = "Repository Metrics",
      description = "Code metrics and statistics",
      mimeType = "application/json",
    ) {
      ReadResourceResult(
        contents =
          listOf(
            TextResourceContents(
              uri = "repo://metrics",
              mimeType = "application/json",
              text =
                """
            {
              "totalFiles": 0,
              "linesOfCode": 0,
              "languages": [],
              "lastAnalyzed": null,
              "complexity": "unknown"
            }
            """
                  .trimIndent(),
            )
          )
      )
    }

    logger.info("MCP server configured successfully with 3 tools, 2 prompts, and 2 resources")
    return server
  }
}
