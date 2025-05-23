package mcp.code.analysis.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.mockk.mockk
import org.slf4j.Logger

class CodeAnalyzerPropertyTest :
  StringSpec({
    val logger: Logger = mockk(relaxed = true)
    val analyzer = CodeAnalyzer(logger = logger)

    // Generate valid file paths
    fun getPathGenerator(language: String) =
      Arb.string(1..50).map { base ->
        val validPath = base.replace(Regex("[^a-zA-Z0-9/._-]"), "_")
        val extension =
          when (language) {
            "kotlin" -> ".kt"
            "java" -> ".java"
            "scala" -> ".scala"
            "python" -> ".py"
            "ruby" -> ".rb"
            "javascript" -> ".js"
            "typescript" -> ".ts"
            "go" -> ".go"
            "c" -> ".c"
            "cpp" -> ".cpp"
            "rust" -> ".rs"
            else -> ".txt"
          }
        if (validPath.lowercase().endsWith(extension)) validPath else "$validPath$extension"
      }

    // Generate language constants
    val languageGenerator = arbitrary {
      listOf("kotlin", "java", "scala", "python", "ruby", "javascript", "typescript", "go", "c", "cpp", "rust").random()
    }

    // Generate code content for different languages
    fun generateCodeForLanguage(language: String): String {
      return when (language) {
        "kotlin" ->
          """
                package test

                // This is a comment
                /* Block comment
                   with multiple lines */
                class TestClass {
                    fun testMethod() {
                        // Method comment
                        val x = 1
                    }
                }

                object Singleton {
                    val constant = 42
                }
            """
            .trimIndent()

        "java" ->
          """
                package test;

                // This is a comment
                /* Block comment
                   with multiple lines */
                public class TestClass {
                    // Field comment
                    private int field;

                    public void testMethod() {
                        // Method comment
                        int x = 1;
                    }
                }

                interface TestInterface {
                    void testMethod();
                }
            """
            .trimIndent()

        "scala" ->
          """
                package test

                // This is a comment
                /* Block comment
                   with multiple lines */
                class TestClass {
                  def testMethod(): Unit = {
                    // Method comment
                    val x = 1
                  }
                }

                object Singleton {
                  val constant = 42
                }

                trait TestTrait {
                  def abstractMethod(): Unit
                }
            """
            .trimIndent()

        "python" ->
          """
                # This is a comment

                \"\"\"
      Block comment
              with multiple lines
        \"\"\"

                def test_function():
                    # Function comment
                    x = 1

                class TestClass:
                    \"\"\"Class docstring\"\"\"
                    def __init__(self):
                        self.value = 42

                    def method(self):
                        return self.value
            """
            .trimIndent()

        "ruby" ->
          """
                # This is a comment

                =begin
                Block comment
                with multiple lines
                =end

                def test_method
                  # Method comment
                  x = 1
                end

                class TestClass
                  def initialize
                    @value = 42
                  end

                  def method
                    @value
                  end
                end

                module TestModule
                  def self.module_method
                    puts "Hello"
                  end
                end
            """
            .trimIndent()

        "javascript",
        "typescript" ->
          """
                // This is a comment

                /* Block comment
                   with multiple lines */

                function testFunction() {
                    // Function comment
                    const x = 1;
                }

                class TestClass {
                    constructor() {
                        this.value = 42;
                    }

                    method() {
                        return this.value;
                    }
                }

                const arrowFn = () => {
                    return "Hello";
                };
            """
            .trimIndent()

        "go" ->
          """
                package test

                // This is a comment

                /* Block comment
                   with multiple lines */

                func testFunction() {
                    // Function comment
                    x := 1
                }

                type TestStruct struct {
                    Value int
                }

                func (t TestStruct) Method() int {
                    return t.Value
                }

                type TestInterface interface {
                    Method() int
                }
            """
            .trimIndent()

        "c",
        "cpp" ->
          """
                // This is a comment

                /* Block comment
                   with multiple lines */

                void testFunction() {
                    // Function comment
                    int x = 1;
                }

                struct TestStruct {
                    int value;
                };

                class TestClass {
                public:
                    TestClass() : value(42) {}

                    int method() {
                        return value;
                    }

                private:
                    int value;
                };
            """
            .trimIndent()

        "rust" ->
          """
                // This is a comment

                /* Block comment
                   with multiple lines */

                fn test_function() {
                    // Function comment
                    let x = 1;
                }

                struct TestStruct {
                    value: i32,
                }

                impl TestStruct {
                    fn new() -> Self {
                        TestStruct { value: 42 }
                    }

                    fn method(&self) -> i32 {
                        self.value
                    }
                }

                trait TestTrait {
                    fn trait_method(&self) -> i32;
                }
            """
            .trimIndent()

        else ->
          """
                // Default comment

                /* Default block comment */

                function defaultFunction() {
                    // Function comment
                }

                class DefaultClass {
                    method() {}
                }
            """
            .trimIndent()
      }
    }

    "summarizeCodeContent should correctly extract definitions and comments for all languages" {
      checkAll(100, languageGenerator) { language ->
        checkAll(100, getPathGenerator(language)) { path ->
          val content = generateCodeForLanguage(language)
          val maxLines = 50

          val result = analyzer.summarizeCodeContent(path, language, content, maxLines)

          // Check the output format
          result.shouldStartWith("### File: $path")
          result.shouldContain("~~~$language")
          result.shouldContain("~~~")

          // Check content extraction based on language
          when (language) {
            "kotlin" -> {
              result.shouldContain("class TestClass")
              result.shouldContain("fun testMethod")
              result.shouldContain("object Singleton")
              result.shouldContain("// This is a comment")
              result.shouldContain("/* Block comment")
            }
            "java" -> {
              result.shouldContain("public class TestClass")
              result.shouldContain("interface TestInterface")
              result.shouldContain("// This is a comment")
              result.shouldContain("/* Block comment")
            }
            "scala" -> {
              result.shouldContain("class TestClass")
              result.shouldContain("def testMethod")
              result.shouldContain("object Singleton")
              result.shouldContain("trait TestTrait")
              result.shouldContain("// This is a comment")
            }
            "python" -> {
              result.shouldContain("def test_function")
              result.shouldContain("class TestClass")
              result.shouldContain("# This is a comment")
            }
            "ruby" -> {
              result.shouldContain("def test_method")
              result.shouldContain("class TestClass")
              result.shouldContain("module TestModule")
              result.shouldContain("# This is a comment")
            }
            "javascript",
            "typescript" -> {
              result.shouldContain("function testFunction")
              result.shouldContain("class TestClass")
              result.shouldContain("const arrowFn")
              result.shouldContain("// This is a comment")
            }
            "go" -> {
              result.shouldContain("func testFunction")
              result.shouldContain("type TestStruct struct")
              result.shouldContain("type TestInterface interface")
              result.shouldContain("// This is a comment")
            }
            "c",
            "cpp" -> {
              result.shouldContain("void testFunction")
              result.shouldContain("struct TestStruct")
              result.shouldContain("class TestClass")
              result.shouldContain("// This is a comment")
            }
            "rust" -> {
              result.shouldContain("fn test_function")
              result.shouldContain("struct TestStruct")
              result.shouldContain("impl TestStruct")
              result.shouldContain("trait TestTrait")
              result.shouldContain("// This is a comment")
            }
          }
        }
      }
    }

    "summarizeCodeContent should respect maxLines parameter" {
      checkAll(100, languageGenerator) { language ->
        checkAll(100, getPathGenerator(language)) { path ->
          val maxLines = 50
          val largeContent = generateCodeForLanguage(language).repeat(10)
          val result = analyzer.summarizeCodeContent(path, language, largeContent, maxLines)

          val resultLines = result.lines().filter { !it.startsWith("### File:") && !it.matches(Regex("^~~~.*$")) }
          assert(resultLines.size <= maxLines) {
            "Result should have at most $maxLines lines, but had ${resultLines.size} instead"
          }
        }
      }
    }

    "summarizeCodeContent should handle empty content" {
      checkAll(100, languageGenerator) { language ->
        checkAll(100, getPathGenerator(language)) { path ->
          val result = analyzer.summarizeCodeContent(path, language, "", 100)

          result.shouldStartWith("### File: $path")
          result.shouldContain("~~~$language")
          result.shouldContain("~~~")
        }
      }
    }

    "summarizeCodeContent should handle content with only comments" {
      checkAll(100, languageGenerator) { language ->
        checkAll(100, getPathGenerator(language)) { path ->
          val commentOnlyContent =
            when (language) {
              "python",
              "ruby" -> "# This is only a comment\n# Another comment line"

              else -> "// This is only a comment\n// Another comment line"
            }

          val result = analyzer.summarizeCodeContent(path, language, commentOnlyContent, 100)

          result.shouldContain(commentOnlyContent)
        }
      }
    }

    "summarizeCodeContent should handle content with only definitions" {
      checkAll(100, languageGenerator) { language ->
        checkAll(100, getPathGenerator(language)) { path ->
          val definitionOnlyContent =
            when (language) {
              "kotlin" -> "class Test {}\nfun testMethod() {}"
              "java" -> "public class Test {}\npublic void testMethod() {}"
              "scala" -> "class Test {}\ndef testMethod() {}"
              "python" -> "class Test:\n    pass\ndef test_function():\n    pass"
              "ruby" -> "class Test\nend\ndef test_method\nend"
              "javascript",
              "typescript" -> "class Test {}\nfunction testMethod() {}"

              "go" -> "type Test struct {}\nfunc testFunction() {}"
              "c",
              "cpp" -> "struct Test {};\nvoid testFunction() {}"

              "rust" -> "struct Test {}\nfn test_function() {}"
              else -> "class Test {}\nfunction testMethod() {}"
            }

          val result = analyzer.summarizeCodeContent(path, language, definitionOnlyContent, 100)

          // The definitions should be included in the summary
          when (language) {
            "kotlin" -> {
              result.shouldContain("class Test")
              result.shouldContain("fun testMethod")
            }

            "java" -> {
              result.shouldContain("public class Test")
              result.shouldContain("public void testMethod")
            }

            "scala" -> {
              result.shouldContain("class Test")
              result.shouldContain("def testMethod")
            }

            "python" -> {
              result.shouldContain("class Test")
              result.shouldContain("def test_function")
            }

            "ruby" -> {
              result.shouldContain("class Test")
              result.shouldContain("def test_method")
            }

            "javascript",
            "typescript" -> {
              result.shouldContain("class Test")
              result.shouldContain("function testMethod")
            }

            "go" -> {
              result.shouldContain("type Test struct")
              result.shouldContain("func testFunction")
            }

            "c",
            "cpp" -> {
              result.shouldContain("struct Test")
              result.shouldContain("void testFunction")
            }

            "rust" -> {
              result.shouldContain("struct Test")
              result.shouldContain("fn test_function")
            }

            else -> {
              result.shouldContain("class Test")
              result.shouldContain("function testMethod")
            }
          }
        }
      }
    }
  })
