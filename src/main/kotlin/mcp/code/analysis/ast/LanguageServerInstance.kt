package mcp.code.analysis.ast

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import mcp.code.analysis.service.lsp.LanguageServerInstance
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either

/**
 * LanguageServerInstance is a class that provides a template for language server instances. It
 * defines the methods to start the server, analyze files, and serialize symbols.
 */
class KotlinLanguageServerInstance(workingDir: File) : LanguageServerInstance(workingDir) {
  override fun getServerCommand(): List<String> {
    return listOf("kotlin-language-server")
  }

  override fun analyzeFile(file: File): JsonObject {
    val server = startServer()
    val documentId = TextDocumentIdentifier(file.toURI().toString())

    // Open the document in the language server
    server.textDocumentService.didOpen(DidOpenTextDocumentParams(getTextDocument(file)))

    // Get document symbols as AST approximation (standard LSP)
    val symbols =
      server.textDocumentService
        .documentSymbol(DocumentSymbolParams(documentId))
        .get(15, TimeUnit.SECONDS)

    // Close the document
    server.textDocumentService.didClose(DidCloseTextDocumentParams(documentId))

    return buildJsonObject {
      put("type", JsonPrimitive("kotlin"))
      put("uri", JsonPrimitive(file.toURI().toString()))
      put("ast", serializeSymbols(symbols))
    }
  }

  private fun serializeSymbols(
    symbols: List<Either<SymbolInformation, DocumentSymbol>>
  ): JsonArray {
    return buildJsonArray {
      symbols.forEach { either ->
        either.map(
          { symbolInfo -> add(serializeSymbolInfo(symbolInfo)) },
          { docSymbol -> add(serializeDocumentSymbol(docSymbol)) },
        )
      }
    }
  }

  private fun serializeSymbolInfo(info: SymbolInformation): JsonObject {
    return buildJsonObject {
      put("name", JsonPrimitive(info.name))
      put("kind", JsonPrimitive(info.kind.toString()))
      put("location", serializeLocation(info.location))
    }
  }

  private fun serializeDocumentSymbol(symbol: DocumentSymbol): JsonObject {
    return buildJsonObject {
      put("name", JsonPrimitive(symbol.name))
      put("kind", JsonPrimitive(symbol.kind.toString()))
      put("range", serializeRange(symbol.range))
      put("selectionRange", serializeRange(symbol.selectionRange))
      put("detail", JsonPrimitive(symbol.detail ?: ""))
      putJsonArray("children") {
        symbol.children?.forEach { child -> add(serializeDocumentSymbol(child)) }
      }
    }
  }

  private fun serializeLocation(location: Location): JsonObject {
    return buildJsonObject {
      put("uri", JsonPrimitive(location.uri))
      put("range", serializeRange(location.range))
    }
  }

  private fun serializeRange(range: Range): JsonObject {
    return buildJsonObject {
      put("start", serializePosition(range.start))
      put("end", serializePosition(range.end))
    }
  }

  private fun serializePosition(position: Position): JsonObject {
    return buildJsonObject {
      put("line", JsonPrimitive(position.line))
      put("character", JsonPrimitive(position.character))
    }
  }
}

class JavaLanguageServerInstance(workingDir: File) : LanguageServerInstance(workingDir) {
  override fun getServerCommand(): List<String> {
    // Eclipse JDT LS
    val jdtPath = System.getenv("JDT_LS_PATH") ?: "/opt/jdt-language-server"

    return listOf(
      "java",
      "-jar",
      "$jdtPath/plugins/org.eclipse.equinox.launcher_*.jar",
      "-configuration",
      "$jdtPath/config",
    )
  }

  override fun analyzeFile(file: File): JsonObject {
    val server = startServer()
    val documentId = TextDocumentIdentifier(file.toURI().toString())

    server.textDocumentService.didOpen(DidOpenTextDocumentParams(getTextDocument(file)))

    val symbols =
      server.textDocumentService
        .documentSymbol(DocumentSymbolParams(documentId))
        .get(15, TimeUnit.SECONDS)

    server.textDocumentService.didClose(DidCloseTextDocumentParams(documentId))

    return buildJsonObject {
      put("type", JsonPrimitive("java"))
      put("uri", JsonPrimitive(file.toURI().toString()))
      put("ast", serializeSymbols(symbols))
    }
  }

  // Reuse the same serialization code as KotlinLanguageServerInstance
  private fun serializeSymbols(
    symbols: List<Either<SymbolInformation, DocumentSymbol>>
  ): JsonArray {
    return buildJsonArray {
      symbols.forEach { either ->
        either.map(
          { symbolInfo ->
            add(
              buildJsonObject {
                put("name", JsonPrimitive(symbolInfo.name))
                put("kind", JsonPrimitive(symbolInfo.kind.toString()))
                put(
                  "location",
                  buildJsonObject {
                    put("uri", JsonPrimitive(symbolInfo.location.uri))
                    put("range", serializeRange(symbolInfo.location.range))
                  },
                )
              }
            )
          },
          { docSymbol -> add(serializeDocumentSymbol(docSymbol)) },
        )
      }
    }
  }

  private fun serializeDocumentSymbol(symbol: DocumentSymbol): JsonObject {
    return buildJsonObject {
      put("name", JsonPrimitive(symbol.name))
      put("kind", JsonPrimitive(symbol.kind.toString()))
      put("range", serializeRange(symbol.range))
      put("selectionRange", serializeRange(symbol.selectionRange))
      put("detail", JsonPrimitive(symbol.detail ?: ""))
      putJsonArray("children") {
        symbol.children?.forEach { child -> add(serializeDocumentSymbol(child)) }
      }
    }
  }

  private fun serializeRange(range: Range): JsonObject {
    return buildJsonObject {
      put(
        "start",
        buildJsonObject {
          put("line", JsonPrimitive(range.start.line))
          put("character", JsonPrimitive(range.start.character))
        },
      )
      put(
        "end",
        buildJsonObject {
          put("line", JsonPrimitive(range.end.line))
          put("character", JsonPrimitive(range.end.character))
        },
      )
    }
  }
}

class PythonLanguageServerInstance(workingDir: File) : LanguageServerInstance(workingDir) {
  override fun getServerCommand(): List<String> {
    return listOf("pylsp") // Python Language Server
  }

  override fun analyzeFile(file: File): JsonObject {
    val server = startServer()
    val documentId = TextDocumentIdentifier(file.toURI().toString())

    server.textDocumentService.didOpen(DidOpenTextDocumentParams(getTextDocument(file)))

    val symbols =
      server.textDocumentService
        .documentSymbol(DocumentSymbolParams(documentId))
        .get(15, TimeUnit.SECONDS)

    server.textDocumentService.didClose(DidCloseTextDocumentParams(documentId))

    return buildJsonObject {
      put("type", JsonPrimitive("python"))
      put("uri", JsonPrimitive(file.toURI().toString()))
      put("ast", serializeSymbols(symbols))
    }
  }

  // Same serialization method as others
  private fun serializeSymbols(
    symbols: List<Either<SymbolInformation, DocumentSymbol>>
  ): JsonArray {
    // Implementation same as KotlinLanguageServerInstance
    return buildJsonArray {
      symbols.forEach { either ->
        either.map(
          { info ->
            add(
              buildJsonObject {
                put("name", JsonPrimitive(info.name))
                put("kind", JsonPrimitive(info.kind.toString()))
              }
            )
          },
          { symbol -> add(serializeDocumentSymbol(symbol)) },
        )
      }
    }
  }

  private fun serializeDocumentSymbol(symbol: DocumentSymbol): JsonObject {
    return buildJsonObject {
      put("name", JsonPrimitive(symbol.name))
      put("kind", JsonPrimitive(symbol.kind.toString()))
      put("detail", JsonPrimitive(symbol.detail ?: ""))
      putJsonArray("children") {
        symbol.children?.forEach { child -> add(serializeDocumentSymbol(child)) }
      }
    }
  }
}

class TypeScriptLanguageServerInstance(workingDir: File) : LanguageServerInstance(workingDir) {
  override fun getServerCommand(): List<String> {
    return listOf("typescript-language-server", "--stdio")
  }

  override fun analyzeFile(file: File): JsonObject {
    val server = startServer()
    val documentId = TextDocumentIdentifier(file.toURI().toString())

    server.textDocumentService.didOpen(DidOpenTextDocumentParams(getTextDocument(file)))

    // For TypeScript, get both document symbols and semantic tokens
    val symbols =
      server.textDocumentService
        .documentSymbol(DocumentSymbolParams(documentId))
        .get(15, TimeUnit.SECONDS)

    server.textDocumentService.didClose(DidCloseTextDocumentParams(documentId))

    return buildJsonObject {
      put("type", JsonPrimitive("typescript"))
      put("uri", JsonPrimitive(file.toURI().toString()))
      put("ast", serializeSymbols(symbols))
    }
  }

  private fun serializeSymbols(
    symbols: List<Either<SymbolInformation, DocumentSymbol>>
  ): JsonArray {
    // Same implementation as others
    return buildJsonArray {
      symbols.forEach { either ->
        either.map(
          { info ->
            add(
              buildJsonObject {
                put("name", JsonPrimitive(info.name))
                put("kind", JsonPrimitive(info.kind.toString()))
              }
            )
          },
          { symbol -> add(serializeDocumentSymbol(symbol)) },
        )
      }
    }
  }

  private fun serializeDocumentSymbol(symbol: DocumentSymbol): JsonObject {
    return buildJsonObject {
      put("name", JsonPrimitive(symbol.name))
      put("kind", JsonPrimitive(symbol.kind.toString()))
      put("detail", JsonPrimitive(symbol.detail ?: ""))
      putJsonArray("children") {
        symbol.children?.forEach { child -> add(serializeDocumentSymbol(child)) }
      }
    }
  }
}

class GoLanguageServerInstance(workingDir: File) : LanguageServerInstance(workingDir) {
  override fun getServerCommand(): List<String> {
    return listOf("gopls", "serve")
  }

  override fun analyzeFile(file: File): JsonObject {
    val server = startServer()
    val documentId = TextDocumentIdentifier(file.toURI().toString())

    server.textDocumentService.didOpen(DidOpenTextDocumentParams(getTextDocument(file)))

    val symbols =
      server.textDocumentService
        .documentSymbol(DocumentSymbolParams(documentId))
        .get(15, TimeUnit.SECONDS)

    server.textDocumentService.didClose(DidCloseTextDocumentParams(documentId))

    return buildJsonObject {
      put("type", JsonPrimitive("go"))
      put("uri", JsonPrimitive(file.toURI().toString()))
      put("ast", serializeSymbols(symbols))
    }
  }

  private fun serializeSymbols(
    symbols: List<Either<SymbolInformation, DocumentSymbol>>
  ): JsonArray {
    // Same implementation
    return buildJsonArray {
      symbols.forEach { either ->
        either.map(
          { info ->
            add(
              buildJsonObject {
                put("name", JsonPrimitive(info.name))
                put("kind", JsonPrimitive(info.kind.toString()))
              }
            )
          },
          { symbol -> add(serializeDocumentSymbol(symbol)) },
        )
      }
    }
  }

  private fun serializeDocumentSymbol(symbol: DocumentSymbol): JsonObject {
    return buildJsonObject {
      put("name", JsonPrimitive(symbol.name))
      put("kind", JsonPrimitive(symbol.kind.toString()))
      put("detail", JsonPrimitive(symbol.detail ?: ""))
      putJsonArray("children") {
        symbol.children?.forEach { child -> add(serializeDocumentSymbol(child)) }
      }
    }
  }
}

class RustLanguageServerInstance(workingDir: File) : LanguageServerInstance(workingDir) {
  override fun getServerCommand(): List<String> {
    return listOf("rust-analyzer")
  }

  override fun analyzeFile(file: File): JsonObject {
    val server = startServer()
    val documentId = TextDocumentIdentifier(file.toURI().toString())

    server.textDocumentService.didOpen(DidOpenTextDocumentParams(getTextDocument(file)))

    val symbols =
      server.textDocumentService
        .documentSymbol(DocumentSymbolParams(documentId))
        .get(15, TimeUnit.SECONDS)

    server.textDocumentService.didClose(DidCloseTextDocumentParams(documentId))

    return buildJsonObject {
      put("type", JsonPrimitive("rust"))
      put("uri", JsonPrimitive(file.toURI().toString()))
      put("ast", serializeSymbols(symbols))
    }
  }

  private fun serializeSymbols(
    symbols: List<Either<SymbolInformation, DocumentSymbol>>
  ): JsonArray {
    // Same implementation
    return buildJsonArray {
      symbols.forEach { either ->
        either.map(
          { info ->
            add(
              buildJsonObject {
                put("name", JsonPrimitive(info.name))
                put("kind", JsonPrimitive(info.kind.toString()))
              }
            )
          },
          { symbol -> add(serializeDocumentSymbol(symbol)) },
        )
      }
    }
  }

  private fun serializeDocumentSymbol(symbol: DocumentSymbol): JsonObject {
    return buildJsonObject {
      put("name", JsonPrimitive(symbol.name))
      put("kind", JsonPrimitive(symbol.kind.toString()))
      put("detail", JsonPrimitive(symbol.detail ?: ""))
      putJsonArray("children") {
        symbol.children?.forEach { child -> add(serializeDocumentSymbol(child)) }
      }
    }
  }
}

class GenericLanguageServerInstance(workingDir: File, private val languageId: String) :
  LanguageServerInstance(workingDir) {
  override fun getServerCommand(): List<String> {
    // Default to a basic text-document server if available
    return listOf("generic-language-server")
  }

  override fun analyzeFile(file: File): JsonObject {
    // For generic files, just return basic info
    return buildJsonObject {
      put("type", JsonPrimitive(languageId))
      put("uri", JsonPrimitive(file.toURI().toString()))
      put("content", JsonPrimitive(file.readText()))
    }
  }
}
