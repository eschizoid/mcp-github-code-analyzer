package mcp.code.analysis.server

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server as SdkServer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import mcp.code.analysis.service.RepositoryAnalysisService
import org.slf4j.Logger

/**
 * Registers MCP tools for repository analysis on the provided server.
 * - **analyze-repository**: Analyzes GitHub repositories to provide comprehensive code insights and structure summary.
 *   Supports any public GitHub repository URL, branch-specific analysis (defaults to 'main'), automatic caching to
 *   prevent duplicate analysis, synchronous analysis for quick responses (up to 20s timeout), and background processing
 *   for large repositories with progress tracking.
 * - **check-analysis-status**: Monitors the progress and completion status of repository analysis operations. Provides
 *   real-time progress tracking for background analyses, status reporting (Running, Completed, Cancelled, Failed),
 *   retrieval of completed analysis results, and error reporting for failed operations.
 * - **cancel-analysis**: Cancels running repository analysis operations with optional cache management. Offers
 *   immediate cancellation of background analysis jobs, optional cache clearing to remove stored results, detailed
 *   feedback on what actions were performed, and backward compatibility with existing usage patterns.
 *
 * @param server The server to register tools on.
 * @param repositoryAnalysisService The service used to perform repository analysis.
 * @param logger The logger to use for logging.
 * @param asyncOperations A map of asynchronous repository analysis operations.
 * @param operationResults A map of cached analysis results.
 * @param operationProgress A map of analysis progress messages.
 */
internal fun registerTools(
  server: SdkServer,
  repositoryAnalysisService: RepositoryAnalysisService,
  logger: Logger,
  asyncOperations: ConcurrentHashMap<String, Job>,
  operationResults: ConcurrentHashMap<String, String>,
  operationProgress: ConcurrentHashMap<String, String>,
) {
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
            CallToolResult(content = listOf(TextContent("Analysis failed: ${progress.substring(16)}")), isError = true)
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
}
