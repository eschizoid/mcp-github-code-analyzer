package mcp.code.analysis.processor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll

class CodeContentProcessorPropertyTest :
  StringSpec({
    // Build the same language patterns used by CodeAnalyzer
    fun createLanguagePatterns(): Map<String, LanguagePatterns> =
      mapOf(
        "kotlin" to
          LanguagePatterns(
            Regex(
              "\\b(class|interface|object|enum\\s+class|data\\s+class|sealed\\s+class|fun|val|var|const|typealias|annotation\\s+class|import|package)\\b"
            ),
            listOf("//"),
            "/*",
            "*/",
          ),
        "scala" to
          LanguagePatterns(
            Regex(
              "\\b(class|object|trait|case\\s+class|case\\s+object|def|val|var|lazy\\s+val|type|implicit|sealed|abstract|override|package\\s+object|import|package)\\b"
            ),
            listOf("//"),
            "/*",
            "*/",
          ),
        "java" to
          LanguagePatterns(
            Regex(
              "\\b(class|interface|enum|@interface|record|public|private|protected|static|abstract|final|synchronized|volatile|native|transient|strictfp|void|import|package)\\b"
            ),
            listOf("//"),
            "/*",
            "*/",
          ),
        "python" to
          LanguagePatterns(
            Regex("\\b(def|class|async\\s+def)\\b|@\\w+|\\b(import|from)\\b"),
            listOf("#"),
            "\"\"\"",
            "\"\"\"",
          ),
        "ruby" to
          LanguagePatterns(
            Regex("\\b(def|class|module|attr_\\w+|require|include|extend)\\b"),
            listOf("#"),
            "=begin",
            "=end",
          ),
        "javascript" to
          LanguagePatterns(
            Regex("\\b(function|class|const|let|var|import|export|interface|type|enum|namespace)\\b"),
            listOf("//"),
            "/*",
            "*/",
          ),
        "typescript" to
          LanguagePatterns(
            Regex("\\b(function|class|const|let|var|import|export|interface|type|enum|namespace)\\b"),
            listOf("//"),
            "/*",
            "*/",
          ),
        "go" to
          LanguagePatterns(
            Regex("\\b(func|type|struct|interface|package|import|var|const)\\b"),
            listOf("//"),
            "/*",
            "*/",
          ),
        "rust" to
          LanguagePatterns(
            Regex("\\b(fn|struct|enum|trait|impl|pub|use|mod|const|static|type|async|unsafe)\\b"),
            listOf("//"),
            "/*",
            "*/",
          ),
        "c" to
          LanguagePatterns(
            Regex("\\b(struct|enum|typedef|void|int|char|bool|extern|static|class)\\b"),
            listOf("//"),
            "/*",
            "*/",
          ),
        "cpp" to
          LanguagePatterns(
            Regex("\\b(class|struct|enum|typedef|namespace|template|void|int|char|bool|auto|extern|static|virtual)\\b"),
            listOf("//"),
            "/*",
            "*/",
          ),
        "default" to
          LanguagePatterns(
            Regex("\\b(class|interface|object|enum|fun|def|function|public|private|protected|static)\\b"),
            listOf("//", "#"),
            "/*",
            "*/",
          ),
      )

    val langPatterns = createLanguagePatterns()

    // Language generator (same set tested in CodeAnalyzerPropertyTest)
    val languageGenerator = arbitrary {
      listOf("kotlin", "java", "scala", "python", "ruby", "javascript", "typescript", "go", "c", "cpp", "rust").random()
    }

    // Generate code content for different languages (duplicated from CodeAnalyzerPropertyTest)
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
            """
            .trimIndent()
      }
    }

    fun containsSubstring(lines: List<String>, substr: String): Boolean = lines.any { it.contains(substr) }

    "processContent should extract definitions and comments for all languages" {
      checkAll(50, languageGenerator) { language ->
        val patterns = langPatterns[language] ?: error("Missing patterns for $language")
        val processor = CodeContentProcessor(patterns, 100)
        val content = generateCodeForLanguage(language)
        val result = processor.processContent(content.lines())

        when (language) {
          "kotlin" -> {
            assert(containsSubstring(result, "class TestClass"))
            assert(containsSubstring(result, "fun testMethod"))
            assert(containsSubstring(result, "object Singleton"))
            assert(containsSubstring(result, "// This is a comment"))
            assert(containsSubstring(result, "/* Block comment"))
          }
          "java" -> {
            assert(containsSubstring(result, "public class TestClass"))
            assert(containsSubstring(result, "interface TestInterface"))
            assert(containsSubstring(result, "// This is a comment"))
            assert(containsSubstring(result, "/* Block comment"))
          }
          "scala" -> {
            assert(containsSubstring(result, "class TestClass"))
            assert(containsSubstring(result, "def testMethod"))
            assert(containsSubstring(result, "object Singleton"))
            assert(containsSubstring(result, "trait TestTrait"))
            assert(containsSubstring(result, "// This is a comment"))
          }
          "python" -> {
            assert(containsSubstring(result, "def test_function"))
            assert(containsSubstring(result, "class TestClass"))
            assert(containsSubstring(result, "# This is a comment"))
          }
          "ruby" -> {
            assert(containsSubstring(result, "def test_method"))
            assert(containsSubstring(result, "class TestClass"))
            assert(containsSubstring(result, "module TestModule"))
            assert(containsSubstring(result, "# This is a comment"))
          }
          "javascript",
          "typescript" -> {
            assert(containsSubstring(result, "function testFunction"))
            assert(containsSubstring(result, "class TestClass"))
            assert(containsSubstring(result, "const arrowFn"))
            assert(containsSubstring(result, "// This is a comment"))
          }
          "go" -> {
            assert(containsSubstring(result, "func testFunction"))
            assert(containsSubstring(result, "type TestStruct struct"))
            assert(containsSubstring(result, "type TestInterface interface"))
            assert(containsSubstring(result, "// This is a comment"))
          }
          "c",
          "cpp" -> {
            assert(containsSubstring(result, "void testFunction"))
            assert(containsSubstring(result, "struct TestStruct"))
            assert(containsSubstring(result, "class TestClass"))
            assert(containsSubstring(result, "// This is a comment"))
          }
          "rust" -> {
            assert(containsSubstring(result, "fn test_function"))
            assert(containsSubstring(result, "struct TestStruct"))
            assert(containsSubstring(result, "impl TestStruct"))
            assert(containsSubstring(result, "trait TestTrait"))
            assert(containsSubstring(result, "// This is a comment"))
          }
        }
      }
    }

    "processContent should respect maxLines parameter" {
      checkAll(50, languageGenerator) { language ->
        val patterns = langPatterns[language] ?: error("Missing patterns for $language")
        val processor = CodeContentProcessor(patterns, 10)
        val largeContent = generateCodeForLanguage(language).repeat(10)
        val result = processor.processContent(largeContent.lines())
        assert(result.size <= 10) { "Result should have at most 10 lines, but had ${'$'}{result.size}" }
      }
    }

    "processContent should handle empty content" {
      checkAll(20, languageGenerator) { language ->
        val patterns = langPatterns[language] ?: error("Missing patterns for $language")
        val processor = CodeContentProcessor(patterns, 100)
        val result = processor.processContent(emptyList())
        assert(result.isEmpty()) { "Expected empty result for empty input" }
      }
    }

    "processContent should handle content with only comments" {
      checkAll(20, languageGenerator) { language ->
        val patterns = langPatterns[language] ?: error("Missing patterns for $language")
        val processor = CodeContentProcessor(patterns, 100)
        val commentOnlyContent =
          if (language in listOf("python", "ruby")) "# This is only a comment\n# Another comment line"
          else "// This is only a comment\n// Another comment line"
        val result = processor.processContent(commentOnlyContent.lines())
        result.shouldContain(commentOnlyContent.lines()[0])
        result.shouldContain(commentOnlyContent.lines()[1])
      }
    }

    "processContent should handle content with only definitions" {
      checkAll(20, languageGenerator) { language ->
        val patterns = langPatterns[language] ?: error("Missing patterns for $language")
        val processor = CodeContentProcessor(patterns, 100)
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
        val result = processor.processContent(definitionOnlyContent.lines())
        when (language) {
          "kotlin" -> {
            assert(containsSubstring(result, "class Test"))
            assert(containsSubstring(result, "fun testMethod"))
          }
          "java" -> {
            assert(containsSubstring(result, "public class Test"))
            assert(containsSubstring(result, "public void testMethod"))
          }
          "scala" -> {
            assert(containsSubstring(result, "class Test"))
            assert(containsSubstring(result, "def testMethod"))
          }
          "python" -> {
            assert(containsSubstring(result, "class Test"))
            assert(containsSubstring(result, "def test_function"))
          }
          "ruby" -> {
            assert(containsSubstring(result, "class Test"))
            assert(containsSubstring(result, "def test_method"))
          }
          "javascript",
          "typescript" -> {
            assert(containsSubstring(result, "class Test"))
            assert(containsSubstring(result, "function testMethod"))
          }
          "go" -> {
            assert(containsSubstring(result, "type Test struct"))
            assert(containsSubstring(result, "func testFunction"))
          }
          "c",
          "cpp" -> {
            assert(containsSubstring(result, "struct Test"))
            assert(containsSubstring(result, "void testFunction"))
          }
          "rust" -> {
            assert(containsSubstring(result, "struct Test"))
            assert(containsSubstring(result, "fn test_function"))
          }
          else -> {
            assert(containsSubstring(result, "class Test"))
            assert(containsSubstring(result, "function testMethod"))
          }
        }
      }
    }
  })
