package mcp.code.analysis.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class AnalysisResult(
    val id: String,
    val status: String,
    val summary: String? = null,
    val codeStructure: JsonElement? = null,
    val insights: List<String>? = null
)

class RepositoryAnalysisService {
    private val analyses = ConcurrentHashMap<String, AnalysisResult>()
    private val gitService = GitService()
    private val codeAnalyzer = CodeAnalyzer()
    private val modelContextService = ModelContextService()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyzeRepository(repoUrl: String, branch: String, analysisType: String): AnalysisResult {
        val id = UUID.randomUUID().toString()

        // Create initial result with pending status
        val initialResult = AnalysisResult(id, "pending")
        analyses[id] = initialResult

        // Start analysis in background
        withContext(Dispatchers.IO) {
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
                val codeSnippets = when (analysisType) {
                    "comprehensive" -> modelContextService.collectAllCodeSnippets(repoDir)
                    "core" -> modelContextService.collectCoreCodeSnippets(repoDir)
                    else -> modelContextService.collectImportantFiles(repoDir)
                }

                val prompt = modelContextService.buildPrompt(codeSnippets, analysisType)
                val response = modelContextService.generateResponse(prompt)
                val insights = modelContextService.parseInsights(response)

                // Create summary
                val summary = modelContextService.generateSummary(repoDir, codeStructure, insights)

                // Update with final result
                analyses[id] = AnalysisResult(
                    id = id,
                    status = "completed",
                    summary = summary,
                    codeStructure = codeStructureJson,
                    insights = insights
                )
            } catch (e: Exception) {
                analyses[id] = initialResult.copy(
                    status = "failed",
                    summary = "Analysis failed: ${e.message}"
                )
            }
        }

        return initialResult
    }

    fun getAnalysisStatus(id: String): AnalysisResult {
        return analyses[id] ?: throw NoSuchElementException("No analysis found with ID: $id")
    }

    // Helper function to convert Map<String, Any> to JsonElement recursively
    private fun convertToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is Map<*, *> -> {
                buildJsonObject {
                    (value as Map<*, *>).forEach { (k, v) ->
                        if (k is String) {
                            put(k, convertToJsonElement(v))
                        }
                    }
                }
            }
            is List<*> -> {
                buildJsonArray {
                    (value as List<*>).forEach { item ->
                        add(convertToJsonElement(item))
                    }
                }
            }
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
    }
}
