package mcp.code.analysis.service

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.readText
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mcp.code.analysis.config.AppConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.Logger

class ModelContextServiceTest {
  // Test cases for parseInsights function
  data class ParseInsightsTestCase(val name: String, val input: String, val expected: List<String>)

  @Test
  fun `test parseInsights with table cases`() {
    val testCases =
      listOf(
        ParseInsightsTestCase(
          name = "numbered list format",
          input = "Here are some insights:\n1. First insight\n2. Second insight\n3. Third insight",
          expected = listOf("Here are some insights:", "First insight", "Second insight", "Third insight"),
        ),
        ParseInsightsTestCase(
          name = "bulleted list format",
          input = "Key points:\n- Point one\n- Point two\n- Point three",
          expected = listOf("Key points:", "Point one", "Point two", "Point three"),
        ),
        ParseInsightsTestCase(
          name = "mixed format",
          input = "Mixed list:\n1. First numbered\n- First bullet\n2. Second numbered",
          expected = listOf("Mixed list:", "First numbered", "First bullet", "Second numbered"),
        ),
        ParseInsightsTestCase(name = "empty input", input = "", expected = emptyList()),
      )

    val service = ModelContextService(logger = mockk(relaxed = true))

    testCases.forEach { testCase ->
      val result = service.parseInsights(testCase.input)
      assertEquals(testCase.expected, result, "Test case '${testCase.name}' failed")
    }
  }

  data class BuildPromptTestCase(
    val name: String,
    val codeSnippets: List<String>,
    val readmeContent: String,
    val astSnippets: List<String>,
    val shouldContain: List<String>,
  )

  @Test
  @Disabled("For now")
  fun `test buildPrompt with table cases`() {
    val testCases =
      listOf(
        BuildPromptTestCase(
          name = "basic prompt",
          codeSnippets = listOf("~~~kotlin\nfun main() { println(\"Hello\") }\n~~~"),
          readmeContent = "# Test Project",
          astSnippets = emptyList(),
          shouldContain =
            listOf(
              "senior developer",
              "# Test Project",
              "~~~kotlin\nfun main() { println(\"Hello\") }\n~~~",
              "Project Summary",
              "High-Level Architecture",
            ),
        ),
        BuildPromptTestCase(
          name = "with AST snippets",
          codeSnippets = listOf("~~~java\nclass Test {}\n~~~"),
          readmeContent = "Empty",
          astSnippets = listOf("AST for Test class"),
          shouldContain = listOf("~~~java\nclass Test {}\n~~~", "Empty", "AST for Test class"),
        ),
        BuildPromptTestCase(
          name = "multiple file snippets",
          codeSnippets =
            listOf(
              "---\n### File: src/main/kotlin/Example.kt\n~~~kotlin\nfun main() {\n  println(\"Hello World\")\n}\n~~~",
              "---\n### File: src/main/java/JavaClass.java\n~~~java\npublic class JavaClass {\n  public static void main(String[] args) {\n    System.out.println(\"Hello from Java\");\n  }\n}\n~~~",
              "---\n### File: src/main/python/script.py\n~~~python\ndef hello():\n    print(\"Hello from Python\")\n\nif __name__ == \"__main__\":\n    hello()\n~~~",
            ),
          readmeContent = "# Multi-language Project\nA test project with multiple languages.",
          astSnippets = listOf("AST for Example.kt", "AST for JavaClass.java"),
          shouldContain =
            listOf(
              "# Multi-language Project",
              "fun main() {",
              "public class JavaClass {",
              "def hello():",
              "AST for Example.kt",
              "AST for JavaClass.java",
            ),
        ),
      )

    val service = ModelContextService(logger = mockk(relaxed = true))

    testCases.forEach { testCase ->
      val result = service.buildPrompt(testCase.codeSnippets)

      testCase.shouldContain.forEach { content ->
        assertTrue(result.contains(content), "Test case '${testCase.name}' should contain '$content'")
      }
    }
  }

  data class GenerateResponseTestCase(
    val name: String,
    val prompt: String,
    val apiResponse: OllamaResponse,
    val expectedResult: String,
    val apiStatus: HttpStatusCode = HttpStatusCode.OK,
  )

  @Test
  fun `test generateResponse with mocked HTTP client`() {
    val mockConfig =
      AppConfig(
        serverPort = 3001,
        githubToken = "test-token",
        cloneDirectory = "/tmp/test",
        logDirectory = "/tmp/test-logs",
        modelApiUrl = "http://test-api.com/api",
        modelApiKey = "test-key",
        modelName = "test-model",
      )

    val testCases =
      listOf(
        GenerateResponseTestCase(
          name = "successful response",
          prompt = "Test prompt",
          apiResponse = OllamaResponse(response = "Generated response"),
          expectedResult = "Generated response",
        ),
        GenerateResponseTestCase(
          name = "empty response",
          prompt = "Test prompt",
          apiResponse = OllamaResponse(response = null),
          expectedResult = "No response generated",
        ),
        // First two test cases remain the same
        GenerateResponseTestCase(
          name = "api error",
          prompt = "Test prompt",
          apiResponse = OllamaResponse(),
          expectedResult = "API error (500 Internal Server Error): Error response",
          apiStatus = HttpStatusCode.InternalServerError,
        ),
      )

    testCases.forEach { testCase ->
      val mockEngine = MockEngine { request ->
        assertEquals("http://test-api.com/api/generate", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)

        // Check request body contains expected values
        val requestBody = request.body.toByteReadPacket().readText()
        assertTrue(requestBody.contains("\"model\":\"test-model\""))
        assertTrue(requestBody.contains("\"prompt\":\"${testCase.prompt}\""))

        if (testCase.apiStatus.isSuccess()) {
          respond(
            content = Json.encodeToString(OllamaResponse.serializer(), testCase.apiResponse),
            status = testCase.apiStatus,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
          )
        } else {
          respond(content = "Error response", status = testCase.apiStatus)
        }
      }

      val mockClient =
        HttpClient(mockEngine) {
          install(ContentNegotiation) {
            json(
              Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                isLenient = true
              }
            )
          }
        }

      val service = ModelContextService(config = mockConfig, httpClient = mockClient, logger = mockk(relaxed = true))

      runBlocking {
        val result = service.generateResponse(testCase.prompt)
        assertEquals(testCase.expectedResult, result, "Test case '${testCase.name}' failed")
      }
    }
  }

  @Test
  fun `test generateSummary calls buildSummaryPrompt and generateResponse`() = runBlocking {
    val mockConfig =
      AppConfig(
        serverPort = 3000,
        githubToken = "test-token",
        cloneDirectory = "/tmp",
        logDirectory = "/tmp",
        modelApiUrl = "http://test-api.com/api",
        modelApiKey = "test-key",
        modelName = "test-model",
      )

    val mockLogger = mockk<Logger>(relaxed = true)

    val mockEngine = MockEngine { request ->
      assertEquals("http://test-api.com/api/generate", request.url.toString())
      assertEquals(HttpMethod.Post, request.method)

      respond(
        content = """{"response":"Summary response"}""",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
      )
    }

    val mockClient =
      HttpClient(mockEngine) {
        install(ContentNegotiation) {
          json(
            Json {
              ignoreUnknownKeys = true
              isLenient = true
            }
          )
        }
      }

    val service = ModelContextService(config = mockConfig, httpClient = mockClient, logger = mockLogger)

    val codeStructure = mapOf("src" to mapOf("Main.kt" to "data"))
    val insights = listOf("Insight 1", "Insight 2")
    val readmeContent = "# Test Project"

    val result = service.generateSummary(codeStructure, insights, readmeContent)

    assertEquals("Summary response", result)
  }
}
