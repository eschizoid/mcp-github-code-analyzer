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
    if (lines.isEmpty()) return emptyList()

    // First pass: decide which original lines should be included, tracking comment block state
    val includeFlags = BooleanArray(lines.size)
    var inCommentBlock = false
    lines.forEachIndexed { idx, line ->
      val trimmed = line.trim()
      val shouldInclude = isDefinition(line) || isCommentLine(line) || inCommentBlock
      includeFlags[idx] = shouldInclude
      val nextInCommentBlock = determineCommentBlockState(trimmed, inCommentBlock)
      inCommentBlock = nextInCommentBlock
    }

    // Second pass: build output with explicit separators between non-contiguous regions
    val result = mutableListOf<String>()
    var lastIncludedIndex = -2 // ensure the first included line does not trigger separator logic

    fun maybeAddSeparator(nextIndex: Int): Boolean {
      // Return true if a separator was added
      if (result.isEmpty()) return false
      val isGap = nextIndex != lastIncludedIndex + 1
      if (!isGap) return false
      // Ensure there is room for the separator and at least one code line
      if (result.size + 2 > maxLines) return false
      result.add("...")
      return true
    }

    for (i in lines.indices) {
      if (!includeFlags[i]) continue

      // If there is a gap from the last included line, insert a separator if we have room
      maybeAddSeparator(i)

      // Prepare the line to add, possibly normalizing definitions
      val line = lines[i]
      val toAdd = if (isDefinition(line)) processDefinitionLine(line) else line

      // Respect maxLines strictly, prioritizing code lines over separators
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
