package mcp.code.analysis.service

import io.mockk.mockk
import java.io.File
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.Logger

class CodeAnalyzerTest {
  @TempDir lateinit var tempDir: File

  private lateinit var logger: Logger
  private lateinit var analyzer: CodeAnalyzer

  @BeforeEach
  fun setUp() {
    logger = mockk(relaxed = true)
    analyzer = CodeAnalyzer(logger = logger)
  }

  // Test cases for findReadmeFile function
  data class ReadmeTestCase(
    val name: String,
    val files: Map<String, String>,
    val expectedContent: String,
    val containsLogMessage: String,
  )

  @Test
  fun `test findReadmeFile with various scenarios`() {
    val testCases =
      listOf(
        ReadmeTestCase(
          name = "standard README.md",
          files =
            mapOf(
              "README.md" to
                """# Project
                   |This is a test project"""
                  .trimMargin()
            ),
          expectedContent =
            """# Project
               |This is a test project"""
              .trimMargin(),
          containsLogMessage = "Readme file found",
        ),
        ReadmeTestCase(
          name = "alternate case readme.md",
          files = mapOf("readme.md" to "# Lowercase README"),
          expectedContent = "# Lowercase README",
          containsLogMessage = "Readme file found",
        ),
        ReadmeTestCase(
          name = "README.txt format",
          files = mapOf("README.txt" to "Plain text readme"),
          expectedContent = "Plain text readme",
          containsLogMessage = "Readme file found",
        ),
        ReadmeTestCase(
          name = "no readme file",
          files = mapOf("other.txt" to "Not a readme"),
          expectedContent = "No README content available.",
          containsLogMessage = "No readme file found",
        ),
        ReadmeTestCase(
          name = "priority check (README.md over readme.txt)",
          files = mapOf("README.md" to "# Markdown Readme", "readme.txt" to "Text Readme"),
          expectedContent = "# Markdown Readme",
          containsLogMessage = "Readme file found",
        ),
      )

    testCases.forEach { testCase ->
      // Clear the directory and create test files
      tempDir.listFiles()?.forEach { it.delete() }

      testCase.files.forEach { (filename, content) -> File(tempDir, filename).writeText(content) }

      // Execute test
      val result = analyzer.findReadmeFile(tempDir)

      // Verify results
      assertEquals(testCase.expectedContent, result, """Test case "${testCase.name}" failed""")
    }
  }

  // Test cases for analyzeStructure function
  @Test
  fun `test analyzeStructure with different directory structures`() {
    // Arrange
    val subDir = File(tempDir, "src").apply { mkdir() }
    File(subDir, "Helper.java").apply { writeText("public class Helper { public void help() {} }") }
    File(tempDir, "main.kt").apply { writeText("""fun main() { println("Hello") }""") }

    // Act
    val result = analyzer.analyzeStructure(tempDir)

    // Assert
    assertTrue(result.containsKey("main.kt"), "Should contain main.kt file")
    assertTrue(result.containsKey("src"), "Should contain src directory")

    val srcContent = result["src"] as? Map<*, *>
    assertNotNull(srcContent, "src should be a map")
    assertTrue(srcContent!!.containsKey("Helper.java"), "src should contain Helper.java")

    val mainKtInfo = result["main.kt"] as? Map<*, *>
    assertNotNull(mainKtInfo, "main.kt should have metadata")
    assertEquals("kotlin", mainKtInfo!!["language"], "Should identify Kotlin language")
  }

  data class SnippetsTestCase(
    val name: String,
    val files: Map<String, String>,
    val expectedSnippetCount: Int,
    val shouldContainFiles: List<String>,
  )

  @Test
  fun `test collectAllCodeSnippets with different file types`() {
    val testCases =
      listOf(
        SnippetsTestCase(
          name = "mixed code files",
          files =
            mapOf(
              "main.kt" to "fun main() {}",
              "helper.java" to "class Helper {}",
              "script.py" to "def hello(): pass",
              "README.md" to "# Not code",
            ),
          expectedSnippetCount = 3,
          shouldContainFiles = listOf("main.kt", "helper.java", "script.py"),
        ),
        SnippetsTestCase(
          name = "exclude test files",
          files = mapOf("src/main.kt" to "fun main() {}", "test/TestMain.kt" to "fun testMain() {}"),
          expectedSnippetCount = 1,
          shouldContainFiles = listOf("src/main.kt"),
        ),
        SnippetsTestCase(
          name = "empty directory",
          files = emptyMap(),
          expectedSnippetCount = 0,
          shouldContainFiles = emptyList(),
        ),
      )

    testCases.forEach { testCase ->
      // Arrange
      tempDir.listFiles()?.forEach { it.deleteRecursively() }

      testCase.files.forEach { (path, content) ->
        val file = File(tempDir, path)
        file.parentFile.mkdirs()
        file.writeText(content)
      }

      // Act
      val snippets = analyzer.collectAllCodeSnippets(tempDir)

      // Assert
      assertEquals(
        testCase.expectedSnippetCount,
        snippets.size,
        """Test case "${testCase.name}" should have ${testCase.expectedSnippetCount} snippets""",
      )

      testCase.shouldContainFiles.forEach { filename ->
        assertTrue(
          snippets.any { it.contains(filename) },
          """Snippets should contain $filename in test "${testCase.name}"""",
        )
      }
    }
  }
}
