package mcp.code.analysis.service

import io.mockk.mockk
import java.io.File
import kotlin.collections.get
import mcp.code.analysis.processor.CodeAnalyzer
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

  data class ReadmeTestCase(
    val name: String,
    val files: Map<String, String>,
    val expectedContent: String,
    val containsLogMessage: String,
  )

  data class SnippetsTestCase(
    val name: String,
    val files: Map<String, String>,
    val expectedSnippetCount: Int,
    val shouldContainFiles: List<String>,
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
                """|# Project
                   |This is a test project"""
                  .trimMargin()
            ),
          expectedContent =
            """|# Project
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

  @Test
  fun `test analyzeStructure with realistic directory structures`() {
    // Arrange
    val subDir = File(tempDir, "src").apply { mkdir() }

    // Java file with imports, package, class and methods
    File(tempDir, "main.java").apply {
      parentFile.mkdirs()
      writeText(
        """|
      |package com.example;
      |
      |import java.util.List;
      |import java.util.ArrayList;
      |import java.io.IOException;
      |
      |/**
      | * Helper class that provides utility methods
      | */
      |public class Helper {
      |    private final List<String> items;
      |
      |    public Helper() {
      |        this.items = new ArrayList<>();
      |    }
      |
      |    /**
      |     * Add an item to the collection
      |     */
      |    public void addItem(String item) throws IOException {
      |        if (item == null) {
      |            throw new IllegalArgumentException("Item cannot be null");
      |        }
      |        items.add(item);
      |    }
      |
      |    public List<String> getItems() {
      |        return new ArrayList<>(items);
      |    }
      |}
      |"""
          .trimIndent()
      )
    }

    // Kotlin file with imports, classes and functions
    File(tempDir, "main.kt").apply {
      parentFile.mkdirs()
      writeText(
        """|
      |
      |package com.example
      |
      |import java.time.LocalDateTime
      |import kotlin.math.max
      |
      |/**
      | * Main application entry point
      | */
      |fun main() {
      |    val app = Application()
      |    app.run()
      |}
      |
      |// Configuration data class
      |data class Config(
      |    val name: String,
      |    val version: String,
      |    val timestamp: LocalDateTime = LocalDateTime.now()
      |)
      |
      |class Application {
      |    private val config = Config(
      |        name = "Demo App",
      |        version = "1.0.0"
      |    )
      |
      |    fun run() {
      |        println("Startin"")
      |        processItems(listOf("apple", "orange", "banana"))
      |    }
      |
      |    private fun processItems(items: List<String>) {
      |        val largest = items.maxByOrNull { it.length }
      |        println("Longest item: 10 with 1000 characters")
      |    }
      |}
      |"""
          .trimIndent()
      )
    }

    // Python file with imports, classes, functions, docstrings
    File(tempDir, "main.py").apply {
      parentFile.mkdirs()
      writeText(
        """|
      |import os
      |import sys
      |from typing import List, Optional
      |from dataclasses import dataclass
      |
      |@dataclass
      |class User:
      |    \"\"\"User data model class\"\"\"
      |    name: str
      |    email: str
      |    active: bool = True
      |
      |class UserRepository:
      |    \"\"\"Handles user data storage and retrieval\"\"\"
      |
      |    def __init__(self):
      |        self.users = {}
      |
      |    def add_user(self, user: User) -> None:
      |        \"\"\"Add a new user to the repository\"\"\"
      |        if user.email in self.users:
      |            raise ValueError(f"User with email {user.email} already exists")
      |        self.users[user.email] = user
      |
      |    def find_by_email(self, email: str) -> Optional[User]:
      |        \"\"\"Retrieve a user by their email\"\"\"
      |        return self.users.get(email)
      |
      |def main():
      |    \"\"\"Main application entry point\"\"\"
      |    repo = UserRepository()
      |    repo.add_user(User(name="John Doe", email="john@example.com"))
      |    repo.add_user(User(name="Jane Smith", email="jane@example.com"))
      |
      |    print(f"Found: {repo.find_by_email('john@example.com')}")
      |
      |if __name__ == "__main__":
      |    main()
      |"""
          .trimIndent()
      )
    }

    // Go file with packages, imports, structs, interfaces, functions
    File(tempDir, "main.go").apply {
      parentFile.mkdirs()
      writeText(
        """|
      |package server
      |
      |import (
      |    "fmt"
      |    "log"
      |    "net/http"
      |    "time"
      |)
      |
      |// Config holds server configuration
      |type Config struct {
      |    Port    int
      |    Timeout time.Duration
      |}
      |
      |// Handler defines the interface for request handlers
      |type Handler interface {
      |    ServeHTTP(w http.ResponseWriter, r *http.Request)
      |}
      |
      |// Server represents an HTTP server instance
      |type Server struct {
      |    config  Config
      |    handler Handler
      |    started bool
      |}
      |
      |// NewServer creates a new server instance
      |func NewServer(config Config, handler Handler) *Server {
      |    return &Server{
      |        config:  config,
      |        handler: handler,
      |    }
      |}
      |
      |// Start begins listening on the configured port
      |func (s *Server) Start() error {
      |    if s.started {
      |        return fmt.Errorf("server already started")
      |    }
      |
      |    addr := fmt.Sprintf(":%d", s.config.Port)
      |    log.Printf("Starting server on %s", addr)
      |
      |    s.started = true
      |    return http.ListenAndServe(addr, s.handler)
      |}
      |"""
          .trimIndent()
      )
    }

    // TypeScript file with imports, interfaces, classes
    File(tempDir, "main.ts").apply {
      parentFile.mkdirs()
      writeText(
        """|
      |import { AxiosInstance, AxiosRequestConfig } from 'axios';
      |import { Observable, from } from 'rxjs';
      |import { map, catchError } from 'rxjs/operators';
      |
      |/**
      | * API response interface
      | */
      |export interface ApiResponse<T> {
      |  data: T;
      |  status: number;
      |  message: string;
      |}
      |
      |/**
      | * Configuration options for the API client
      | */
      |export interface ApiClientOptions {
      |  baseUrl: string;
      |  timeout?: number;
      |  headers?: Record<string, string>;
      |}
      |
      |/**
      | * Client for making API requests
      | */
      |export class ApiClient {
      |  private axios: AxiosInstance;
      |  private options: ApiClientOptions;
      |
      |  constructor(options: ApiClientOptions) {
      |    this.options = {
      |      timeout: 30000,
      |      ...options
      |    };
      |
      |    // Initialize axios instance
      |    this.axios = axios.create({
      |      baseURL: this.options.baseUrl,
      |      timeout: this.options.timeout,
      |      headers: this.options.headers
      |    });
      |  }
      |
      |  /**
      |   * Makes a GET request to the specified endpoint
      |   */
      |  public get<T>(url: string, config?: AxiosRequestConfig): Observable<ApiResponse<T>> {
      |    return from(this.axios.get<ApiResponse<T>>(url, config)).pipe(
      |      map(response => response.data),
      |      catchError(error => {
      |        console.error(API error: error);
      |        throw error;
      |      })
      |    );
      |  }
      |}
    |"""
          .trimIndent()
      )
    }

    // JavaScript file with modules, classes, functions
    File(tempDir, "main.js").apply {
      parentFile.mkdirs()
      writeText(
        """|
      |/**
      | * @fileoverview Utility functions for the application
      | */
      |
      |const crypto = require('crypto');
      |const fs = require('fs').promises;
      |const path = require('path');
      |
      |/**
      | * Generates a secure random token
      | * @param {number} length The desired length of the token
      | * @returns {string} A random token string
      | */
      |function generateToken(length = 32) {
      |  return crypto.randomBytes(length).toString('hex');
      |}
      |
      |/**
      | * File cache implementation
      | */
      |class FileCache {
      |  /**
      |   * @param {number} maxItems Maximum number of items to store in cache
      |   */
      |  constructor(maxItems = 100) {
      |    this.cache = new Map();
      |    this.maxItems = maxItems;
      |  }
      |
      |  /**
      |   * Store a file in cache
      |   * @param {string} filePath Path to the file
      |   * @param {*} contents File contents
      |   */
      |  set(filePath, contents) {
      |    // Evict oldest entry if at capacity
      |    if (this.cache.size >= this.maxItems) {
      |      const oldestKey = this.cache.keys().next().value;
      |      this.cache.delete(oldestKey);
      |    }
      |
      |    this.cache.set(filePath, {
      |      contents,
      |      timestamp: Date.now()
      |    });
      |  }
      |
      |  /**
      |   * Get a file from cache
      |   * @param {string} filePath Path to the file
      |   * @returns {*} File contents or null if not in cache
      |   */
      |  get(filePath) {
      |    const entry = this.cache.get(filePath);
      |    return entry ? entry.contents : null;
      |  }
      |}
      |
      |module.exports = {
      |  generateToken,
      |  FileCache
      |};
    """
          .trimIndent()
      )
    }

    // Ruby file with requires, modules, classes
    File(tempDir, "main.rb").apply {
      parentFile.mkdirs()
      writeText(
        """|
      |require 'json'
      |require 'date'
      |require 'logger'
      |
      |# Configuration module for application settings
      |module Config
      |  extend self
      |
      |  # Load configuration from environment or defaults
      |  def load
      |    {
      |      environment: ENV['APP_ENV'] || 'development',
      |      log_level: ENV['LOG_LEVEL'] || 'info',
      |      max_threads: (ENV['MAX_THREADS'] || '5').to_i
      |    }
      |  end
      |end
      |
      |# Application logger
      |class AppLogger
      |  attr_reader :logger
      |
      |  def initialize(level = :info)
      |    @logger = Logger.new(STDOUT)
      |    @logger.level = Logger.const_get(level.to_s.upcase)
      |  end
      |
      |  def info(message)
      |    @logger.info("[#{timestamp}] #{message}")
      |  end
      |
      |  def error(message)
      |    @logger.error("[#{timestamp}] ERROR: #{message}")
      |  end
      |
      |  private
      |
      |  def timestamp
      |    DateTime.now.strftime('%Y-%m-%d %H:%M:%S')
      |  end
      |end
      |
      |# Application entry point
      |class Application
      |  def initialize
      |    @config = Config.load
      |    @logger = AppLogger.new(@config[:log_level])
      |  end
      |
      |  def run
      |    @logger.info("Starting application in #{@config[:environment]} mode")
      |    @logger.info("Using #{@config[:max_threads]} threads")
      |  end
      |end
      |
      |# Run the application if this file is executed directly
      |if __FILE__ == PROGRAM_NAME
      |  app = Application.new
      |  app.run
      |end
    |"""
          .trimIndent()
      )
    }

    // Scala file with traits, classes, objects
    File(tempDir, "main.scala").apply {
      parentFile.mkdirs()
      writeText(
        """|
      |package com.example
      |
      |import java.time.LocalDateTime
      |import scala.util.{Try, Success, Failure}
      |
      |/**
      | * Entity base trait for domain models
      | */
      |trait Entity {
      |  def id: String
      |  def createdAt: LocalDateTime
      |}
      |
      |/**
      | * User entity implementation
      | */
      |case class User(
      |  id: String,
      |  email: String,
      |  displayName: String,
      |  createdAt: LocalDateTime = LocalDateTime.now(),
      |  active: Boolean = true
      |) extends Entity
      |
      |/**
      | * Repository interface for data access
      | */
      |trait Repository[T <: Entity] {
      |  def findById(id: String): Option[T]
      |  def save(entity: T): Try[T]
      |  def delete(id: String): Try[Unit]
      |}
      |
      |/**
      | * In-memory implementation of the user repository
      | */
      |class InMemoryUserRepository extends Repository[User] {
      |  private var users = Map.empty[String, User]
      |
      |  override def findById(id: String): Option[User] = users.get(id)
      |
      |  override def save(user: User): Try[User] = {
      |    users = users + (user.id -> user)
      |    Success(user)
      |  }
      |
      |  override def delete(id: String): Try[Unit] = {
      |    if (users.contains(id)) {
      |      users = users - id
      |      Success(())
      |    } else {
      |      Failure(new NoSuchElementException(s"User with id 1 not found"))
      |    }
      |  }
      |}
      |
      |/**
      | * Companion object with factory methods
      | */
      |object User {
      |  def create(email: String, displayName: String): User = {
      |    val id = java.util.UUID.randomUUID().toString
      |    User(id, email, displayName)
      |  }
      |}"""
          .trimIndent()
      )
    }

    // Act
    val result = analyzer.analyzeStructure(tempDir)

    // Assert
    assertTrue(result.containsKey("main.java"), "Should contain main.kt file")
    assertTrue(result.containsKey("main.kt"), "Should contain main.kt file")
    assertTrue(result.containsKey("main.scala"), "Should contain main.scala file")
    assertTrue(result.containsKey("main.py"), "Should contain main.py file")
    assertTrue(result.containsKey("main.go"), "Should contain main.go file")
    assertTrue(result.containsKey("main.ts"), "Should contain main.ts file")
    assertTrue(result.containsKey("main.rb"), "Should contain main.rb file")
    assertTrue(result.containsKey("main.js"), "Should contain main.js file")

    val mainJavaInfo = result["main.java"] as? Map<*, *>
    assertNotNull(mainJavaInfo, "main.java should have metadata")
    assertEquals("java", mainJavaInfo!!["language"], "Should identify Java language")

    val mainKtInfo = result["main.kt"] as? Map<*, *>
    assertNotNull(mainKtInfo, "main.kt should have metadata")
    assertEquals("kotlin", mainKtInfo!!["language"], "Should identify Kotlin language")

    val mainScalaInfo = result["main.scala"] as? Map<*, *>
    assertNotNull(mainScalaInfo, "main.scala should have metadata")
    assertEquals("scala", mainScalaInfo!!["language"], "Should identify Scala language")

    val mainPyInfo = result["main.py"] as? Map<*, *>
    assertNotNull(mainPyInfo, "main.py should have metadata")
    assertEquals("python", mainPyInfo!!["language"], "Should identify Python language")

    val mainGoInfo = result["main.go"] as? Map<*, *>
    assertNotNull(mainGoInfo, "main.go should have metadata")
    assertEquals("go", mainGoInfo!!["language"], "Should identify Go language")

    val mainTsInfo = result["main.ts"] as? Map<*, *>
    assertNotNull(mainTsInfo, "main.ts should have metadata")
    assertEquals("typescript", mainTsInfo!!["language"], "Should identify TypeScript language")

    val mainJsInfo = result["main.js"] as? Map<*, *>
    assertNotNull(mainJsInfo, "main.js should have metadata")
    assertEquals("javascript", mainJsInfo!!["language"], "Should identify JavaScript language")

    val mainRbInfo = result["main.rb"] as? Map<*, *>
    assertNotNull(mainRbInfo, "main.rb should have metadata")
    assertEquals("ruby", mainRbInfo!!["language"], "Should identify Ruby language")
  }

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
      val snippets = analyzer.collectSummarizedCodeSnippets(tempDir)

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
