package mcp.code.analysis.service

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mcp.code.analysis.config.AppConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger

class ModelContextServiceTest {
  private lateinit var logger: Logger
  private lateinit var config: AppConfig
  private lateinit var mockHttpClient: HttpClient
  private lateinit var service: ModelContextService

  // Test cases for buildInsightsPrompt function
  data class InsightsPromptTestCase(
    val name: String,
    val readmeContent: String,
    val expectedContains: List<String>,
    val shouldNotContain: List<String> = listOf(),
  )

  // Test cases for generateResponse function
  data class ResponseTestCase(
    val name: String,
    val prompt: String,
    val mockResponse: String,
    val expectedResult: String,
  )

  // Test cases for generateSummary function
  data class SummaryTestCase(
    val name: String,
    val codeStructure: Map<String, Any>,
    val codeSnippets: List<String>,
    val expectedPromptContains: List<String>,
  )

  @BeforeEach
  fun setUp() {
    logger = mockk(relaxed = true)
    config =
      AppConfig(
        serverPort = 3001,
        githubToken = "dummy-token",
        cloneDirectory = "/tmp/test-clones",
        logDirectory = "/tmp/test-logs",
        modelApiUrl = "http://localhost:11434/api",
        modelApiKey = "test-key",
        modelName = "test-model",
      )

    mockHttpClient =
      HttpClient(MockEngine) {
        install(ContentNegotiation) {
          json(
            Json {
              ignoreUnknownKeys = true
              prettyPrint = true
            }
          )
        }
        engine {
          addHandler { request ->
            respond(
              content = """{"model":"test-model","response":"Generated test response","done":true}""",
              status = HttpStatusCode.OK,
              headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
          }
        }
      }

    service = ModelContextService(config = config, httpClient = mockHttpClient, logger = logger)
  }

  @Test
  fun `test buildInsightsPrompt formats README correctly`() {
    val testCases =
      listOf(
        InsightsPromptTestCase(
          name = "standard README with code blocks",
          readmeContent =
            """|# Test Project
               |
               |This is a test project with ```code blocks```
               |
               |## Features
               |- Feature 1
               |- Feature 2
               |"""
              .trimMargin(),
          expectedContains =
            listOf(
              "README Content:",
              "# Test Project",
              "~~~markdown",
              "~~~code blocks~~~", // Check backtick replacement
            ),
          shouldNotContain = listOf("```"),
        ),
        InsightsPromptTestCase(
          name = "empty README",
          readmeContent = "",
          expectedContains = listOf("README Content:", "~~~markdown", "~~~"),
          shouldNotContain = listOf("```"),
        ),
        InsightsPromptTestCase(
          name = "README with multiple code blocks",
          readmeContent =
            """|# Code Examples
               |```kotlin
               |fun test() {}
               |```
               |
               |And more code:
               |```java
               |public void test() {}
               |```"""
              .trimMargin(),
          expectedContains = listOf("~~~kotlin", "~~~java", "fun test() {}"),
          shouldNotContain = listOf("```"),
        ),
      )

    testCases.forEach { testCase ->
      // Act
      val prompt = service.buildInsightsPrompt(testCase.readmeContent)

      // Assert
      testCase.expectedContains.forEach { expected ->
        assertTrue(prompt.contains(expected), "Test case '${testCase.name}' should contain '$expected'")
      }

      testCase.shouldNotContain.forEach { unexpected ->
        assertFalse(prompt.contains(unexpected), "Test case '${testCase.name}' should not contain '$unexpected'")
      }
    }
  }

  @Test
  fun `test generateResponse returns model response`() = runBlocking {
    val testCases =
      listOf(
        ResponseTestCase(
          name = "simple prompt",
          prompt = "Test prompt",
          mockResponse = """{"model":"test-model","response":"Generated test response","done":true}""",
          expectedResult = "Generated test response",
        ),
        ResponseTestCase(
          name = "empty response",
          prompt = "Empty test",
          mockResponse = """{"model":"test-model","response":"","done":true}""",
          expectedResult = "",
        ),
        ResponseTestCase(
          name = "special characters",
          prompt = "Special chars test",
          mockResponse = """{"model":"test-model","response":"Response with \"quotes\" and \n newlines","done":true}""",
          expectedResult = "Response with \"quotes\" and \n newlines",
        ),
      )

    testCases.forEach { testCase ->
      // Arrange
      val testMockClient =
        HttpClient(MockEngine) {
          install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          engine {
            addHandler { request ->
              // Assert
              assertEquals(HttpMethod.Post, request.method)
              assertTrue(request.url.toString().contains("/api"))

              respond(
                content = testCase.mockResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
              )
            }
          }
        }

      val testService = ModelContextService(config, testMockClient, logger)

      // Act
      val response = testService.generateResponse(testCase.prompt)

      // Assert
      assertEquals(testCase.expectedResult, response, "Test case '${testCase.name}' should return expected response")
    }
  }

  @Test
  fun `test generateSummary builds combined prompt with code and insights`() = runBlocking {
    // Arrange
    val testCases =
      listOf(
        SummaryTestCase(
          name = "simple project",
          codeStructure = mapOf("main.kt" to mapOf("language" to "kotlin")),
          codeSnippets =
            listOf(
              """|File: main.kt
                 |~~~kotlin
                 |fun main() {}
                 |~~~"""
                .trimMargin()
            ),
          expectedPromptContains = listOf("Code Snippets:", "fun main() {}"),
        ),
        SummaryTestCase(
          name = "complex project",
          codeStructure =
            mapOf(
              "src" to mapOf("main.kt" to mapOf("language" to "kotlin"), "util.kt" to mapOf("language" to "kotlin"))
            ),
          codeSnippets =
            listOf(
              """|File: src/main.kt
                 |~~~kotlin
                 |fun main() {}
                 |~~~"""
                .trimMargin(),
              """|File: src/util.kt
                 |~~~kotlin
                 |fun util() {}
                 |~~~"""
                .trimMargin(),
            ),
          expectedPromptContains = listOf("Code Snippets:", "fun main() {}", "fun util() {}"),
        ),
        SummaryTestCase(
          name = "empty project",
          codeStructure = emptyMap(),
          codeSnippets = emptyList(),
          expectedPromptContains = listOf("Code Snippets:"),
        ),
      )

    testCases.forEach { testCase ->
      val mockService = mockk<ModelContextService>()
      coEvery { mockService.generateResponse(any()) } returns "Mocked summary response"
      coEvery { mockService.buildSummaryPrompt(any(), any()) } coAnswers
        {
          val codeStructure = firstArg<Map<String, Any>>()
          val codeSnippets = secondArg<List<String>>()

          // Act
          val prompt = service.buildSummaryPrompt(codeStructure, codeSnippets)

          // Assert
          testCase.expectedPromptContains.forEach { expected ->
            assertTrue(prompt.contains(expected), "Prompt for test '${testCase.name}' should contain '$expected'")
          }

          "Mocked summary response"
        }

      // Act
      val summary = mockService.buildSummaryPrompt(testCase.codeStructure, testCase.codeSnippets)

      // Assert
      assertEquals("Mocked summary response", summary)
      coVerify { mockService.buildSummaryPrompt(testCase.codeStructure, testCase.codeSnippets) }
    }
  }
}
