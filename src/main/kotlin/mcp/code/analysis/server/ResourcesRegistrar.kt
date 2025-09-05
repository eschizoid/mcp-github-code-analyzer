package mcp.code.analysis.server

import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.server.Server as SdkServer

/**
 * Registers resource endpoints on the provided MCP server.
 *
 * @param server The MCP server instance to register resources on.
 */
internal fun registerResources(server: SdkServer) {
  server.addResource(
    uri = "repo://analysis-results",
    name = "Repository Analysis Results",
    description = "Latest repository analysis results",
    mimeType = "application/json",
  ) {
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
}
