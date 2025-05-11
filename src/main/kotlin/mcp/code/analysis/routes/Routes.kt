package mcp.code.analysis.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import mcp.code.analysis.service.RepositoryAnalysisService

@Serializable
data class RepositoryRequest(
    val repoUrl: String,
    val branch: String = "main",
    val analysisType: String = "full"
)

fun Application.registerRoutes(application: Application) {
    val analysisService = RepositoryAnalysisService()

    routing {
        get("/") {
            call.respondText("Model Context Protocol Server is running", ContentType.Text.Plain)
        }

        post("/analyze") {
            try {
                val request = call.receive<RepositoryRequest>()
                val result = analysisService.analyzeRepository(request.repoUrl, request.branch, request.analysisType)
                call.respond(result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        get("/status/{id}") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing ID parameter"))
                return@get
            }

            val status = analysisService.getAnalysisStatus(id)
            call.respond(status)
        }
    }
}
