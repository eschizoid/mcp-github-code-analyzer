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
   * The processing follows a two-pass approach:
   * 1. First pass: Decides which original lines should be included, tracking comment block state
   * 2. Second pass: Builds output with explicit separators between non-contiguous regions
   *
   * During the first pass, lines are included if they are definitions, comments, or part of a block comment. The
   * comment block state is tracked to ensure multi-line comments are properly captured.
   *
   * During the second pass, separators ("...") are added between non-contiguous regions to indicate omitted code
   * sections. The maxLines limit is strictly respected, prioritizing code lines over separators.
   *
   * @param lines The lines of code to process.
   * @return A list of lines selected for summarization, with separators indicating omitted sections.
   */
  fun processContent(lines: List<String>): List<String> {
    if (lines.isEmpty()) return emptyList()

    val includeFlags = BooleanArray(lines.size)
    var inCommentBlock = false
    lines.forEachIndexed { idx, line ->
      val trimmed = line.trim()
      val shouldInclude = isDefinition(line) || isCommentLine(line) || inCommentBlock
      includeFlags[idx] = shouldInclude
      val nextInCommentBlock = determineCommentBlockState(trimmed, inCommentBlock)
      inCommentBlock = nextInCommentBlock
    }

    val result = mutableListOf<String>()
    var lastIncludedIndex = -2

    fun maybeAddSeparator(nextIndex: Int): Boolean {
      if (result.isEmpty()) return false
      val isGap = nextIndex != lastIncludedIndex + 1
      if (!isGap) return false
      if (result.size + 2 > maxLines) return false
      result.add("...")
      return true
    }

    for (i in lines.indices) {
      if (!includeFlags[i]) continue

      maybeAddSeparator(i)

      val line = lines[i]
      val toAdd = if (isDefinition(line)) processDefinitionLine(line) else line

      if (result.size >= maxLines) break

      result.add(toAdd)
      lastIncludedIndex = i

      if (result.size >= maxLines) break
    }

    return result
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
