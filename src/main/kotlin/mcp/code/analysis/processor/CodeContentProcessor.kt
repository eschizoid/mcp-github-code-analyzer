package mcp.code.analysis.processor

/**
 * Processes a list of code lines and returns those that should be included in the summary. Lines are included if they
 * are definitions, comments, or part of a block comment.
 *
 * @param lines The lines of code to process.
 * @return A list of lines selected for summarization.
 */
internal class CodeContentProcessor(private val patterns: LanguagePatterns, private val maxLines: Int) {

  /**
   * Processes a list of code lines and returns those that should be included in the summary. Lines are included if they
   * are definitions, comments, or part of a block comment.
   *
   * @param lines The lines of code to process.
   * @return A list of lines selected for summarization.
   */
  fun processContent(lines: List<String>): List<String> {
    val finalState =
      lines.fold(ProcessingState()) { state, line ->
        if (state.lines.size >= maxLines) return@fold state

        val trimmed = line.trim()
        val nextInCommentBlock = determineCommentBlockState(trimmed, state.inCommentBlock)
        val shouldIncludeLine = isDefinition(line) || isCommentLine(line) || state.inCommentBlock

        val updatedLines =
          if (shouldIncludeLine) {
            when {
              isDefinition(line) -> state.lines + processDefinitionLine(line)
              else -> state.lines + line
            }
          } else {
            state.lines
          }

        ProcessingState(updatedLines, nextInCommentBlock)
      }

    return finalState.lines
  }

  private fun isDefinition(line: String): Boolean = patterns.definitionPattern.containsMatchIn(line.trim())

  private fun isCommentLine(line: String): Boolean {
    val trimmed = line.trim()
    return patterns.commentPrefixes.any { trimmed.startsWith(it) } ||
      trimmed.startsWith(patterns.blockCommentStart) ||
      trimmed.startsWith("*") ||
      trimmed.startsWith("/**") ||
      trimmed.startsWith("**/")
  }

  private fun processDefinitionLine(line: String): String {
    val trimmed = line.trim()
    return when {
      trimmed.contains("{") && !trimmed.contains("}") -> "$trimmed }"
      trimmed.endsWith(";") -> trimmed
      trimmed.endsWith(":") -> trimmed // Python, YAML style
      else -> trimmed
    }
  }

  private fun determineCommentBlockState(trimmed: String, currentInCommentBlock: Boolean): Boolean =
    when {
      trimmed.startsWith(patterns.blockCommentStart) && !trimmed.endsWith(patterns.blockCommentEnd) -> true
      trimmed.endsWith(patterns.blockCommentEnd) -> false
      trimmed == "\"\"\"" -> !currentInCommentBlock // Python docstrings
      trimmed.startsWith("'''") && trimmed.endsWith("'''") && trimmed.length > 6 -> currentInCommentBlock // Single line
      trimmed == "'''" -> !currentInCommentBlock // Python triple quotes
      else -> currentInCommentBlock
    }
}
