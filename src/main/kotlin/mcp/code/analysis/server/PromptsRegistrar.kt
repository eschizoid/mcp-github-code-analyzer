package mcp.code.analysis.server

import io.modelcontextprotocol.kotlin.sdk.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.Role
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.Server as SdkServer
import java.util.Locale.getDefault

/**
 * Registers prompt templates on the provided MCP server.
 *
 * @param server The MCP server instance to register prompts on.
 */
internal fun registerPrompts(server: SdkServer) {
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
}
