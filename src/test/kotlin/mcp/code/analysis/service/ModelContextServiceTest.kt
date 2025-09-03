package mcp.code.analysis.service

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.mockk.mockk
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

  // Test cases for buildSummaryPrompt function
  data class SummaryTestCase(val name: String, val insights: String, val expectedPromptContains: List<String>)

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
              content = """{"message":{"role":"assistant","content":"Generated test response"}}""",
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
      val prompt = service.buildInsightsPrompt(emptyList(), testCase.readmeContent)

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
          mockResponse =
            """|{
               |  "message": {
               |    "role": "assistant",
               |    "content": "Generated test response"
               |  }
               |}"""
              .trimMargin(),
          expectedResult = "Generated test response",
        ),
        ResponseTestCase(
          name = "empty response",
          prompt = "Empty test",
          mockResponse =
            """|{
               |  "message": {
               |    "role": "assistant",
               |    "content": ""
               |  }
               |}"""
              .trimMargin(),
          expectedResult = "",
        ),
        ResponseTestCase(
          name = "special characters",
          prompt = "Special chars test",
          mockResponse =
            """|{
               |  "message": {
               |    "role": "assistant",
               |    "content": "Response with \"quotes\" and \n newlines"
               |  }
               |}"""
              .trimMargin(),
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
  fun `test buildSummaryPrompt builds prompt with insights`() = runBlocking {
    // Arrange
    val testCases =
      listOf(
        SummaryTestCase(
          name = "simple insights",
          insights = "Basic code analysis with some insights",
          expectedPromptContains = listOf("Structural Analysis:", "Basic code analysis with some insights"),
        ),
        SummaryTestCase(
          name = "complex insights with file analysis",
          insights =
            """|### File: src/main.kt (Language: Kotlin)
               |- **Purpose**: Main entry point
               |- **Key Components**: main function
               |
               |### File: src/util.kt (Language: Kotlin)
               |- **Purpose**: Utility functions
               |"""
              .trimMargin(),
          expectedPromptContains = listOf("Structural Analysis:", "File: src/main.kt", "File: src/util.kt"),
        ),
        SummaryTestCase(name = "empty insights", insights = "", expectedPromptContains = listOf("Structural Analysis:")),
      )

    testCases.forEach { testCase ->
      // Act
      val prompt = service.buildSummaryPrompt(testCase.insights)

      // Assert
      testCase.expectedPromptContains.forEach { expected ->
        assertTrue(prompt.contains(expected), "Prompt for test '${testCase.name}' should contain '$expected'")
      }
    }
  }

  @Test
  fun `test parseInsights extracts file sections`() {
    // Arrange
    val insights =
      """|Some general information
         |
         |### File: src/main.kt (Language: Kotlin)
         |- **Purpose**: Main entry point
         |
         |### File: src/util.kt (Language: Kotlin)
         |- **Purpose**: Utility functions
         |"""
        .trimMargin()

    // Act
    val parsedInsights = ModelContextService.parseInsights(insights)

    // Assert
    assertTrue(parsedInsights.startsWith("### File:"))
    assertTrue(parsedInsights.contains("src/main.kt"))
    assertTrue(parsedInsights.contains("src/util.kt"))
    assertFalse(parsedInsights.contains("Some general information"))
  }
}
