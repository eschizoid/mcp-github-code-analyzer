package mcp.code.analysis.service

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class AnalysisResult(
  val id: String,
  val status: String,
  val summary: String? = null,
  val codeStructure: JsonElement? = null,
  val insights: List<String>? = null,
)

/**
 * Service for analyzing GitHub repositories.
 *
 * This service provides methods to clone a repository, analyze its code structure, and generate
 * insights based on the analysis.
 */
@Deprecated("Use RepositoryASTAnalyzer instead")
class RepositoryAnalysisService {

  companion object {
    private val analyses = ConcurrentHashMap<String, AnalysisResult>()
  }

  private val gitService = GitService()
  private val codeAnalyzer = CodeAnalyzer()
  private val modelContextService = ModelContextService()

  fun analyzeRepository(repoUrl: String, branch: String, analysisType: String): AnalysisResult {
    val id = UUID.randomUUID().toString()

    // Create an initial result with pending status
    val initialResult = AnalysisResult(id, "pending")
    analyses[id] = initialResult

    // Start analysis in the background
    GlobalScope.launch(Dispatchers.IO) {
      try {
        // Clone repository
        val repoDir = gitService.cloneRepository(repoUrl, branch)

        // Update status to processing
        analyses[id] = initialResult.copy(status = "processing")

        // Analyze code structure
        val codeStructure = codeAnalyzer.analyzeStructure(repoDir)

        // Convert Map<String, Any> to JsonElement for serialization
        val codeStructureJson = convertToJsonElement(codeStructure)

        // Generate insights using appropriate methods
        val codeSnippets =
          when (analysisType) {
            "comprehensive" -> modelContextService.collectAllCodeSnippets(repoDir)
            "core" -> modelContextService.collectCoreCodeSnippets(repoDir)
            "quick" -> modelContextService.collectImportantFiles(repoDir)
            else -> emptyList()
          }

        val prompt = modelContextService.buildPrompt(codeSnippets, analysisType)
        val response = modelContextService.generateResponse(prompt)
        val insights = modelContextService.parseInsights(response)

        // Create summary
        val summary = modelContextService.generateSummary(repoDir, codeStructure, insights)

        // Update with a final result
        analyses[id] =
          AnalysisResult(
            id = id,
            status = "completed",
            summary = summary,
            codeStructure = codeStructureJson,
            insights = insights,
          )
      } catch (e: Exception) {
        analyses[id] =
          initialResult.copy(status = "failed", summary = "Analysis failed: ${e.message}")
      }
    }

    return initialResult
  }

  fun getAnalysisStatus(id: String): AnalysisResult =
    analyses[id] ?: throw NoSuchElementException("No analysis found with ID: $id")

  // Helper function to convert Map<String, Any> to JsonElement recursively
  private fun convertToJsonElement(value: Any?): JsonElement =
    when (value) {
      null -> JsonNull
      is Map<*, *> -> {
        buildJsonObject {
          value.forEach { (k, v) ->
            if (k is String) {
              put(k, convertToJsonElement(v))
            }
          }
        }
      }
      is List<*> -> {
        buildJsonArray { value.forEach { item -> add(convertToJsonElement(item)) } }
      }
      is String -> JsonPrimitive(value)
      is Number -> JsonPrimitive(value)
      is Boolean -> JsonPrimitive(value)
      else -> JsonPrimitive(value.toString())
    }
}
