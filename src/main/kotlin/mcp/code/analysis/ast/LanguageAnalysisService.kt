package mcp.code.analysis.service.lsp

import java.io.File
import java.util.concurrent.*
import kotlinx.serialization.json.*
import mcp.code.analysis.ast.GenericLanguageServerInstance
import mcp.code.analysis.ast.GoLanguageServerInstance
import mcp.code.analysis.ast.JavaLanguageServerInstance
import mcp.code.analysis.ast.KotlinLanguageServerInstance
import mcp.code.analysis.ast.PythonLanguageServerInstance
import mcp.code.analysis.ast.RustLanguageServerInstance
import mcp.code.analysis.ast.TypeScriptLanguageServerInstance
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.*
import org.slf4j.LoggerFactory

/**
 * LanguageAnalysisService is responsible for managing language servers for different programming
 * languages. It detects the language based on file extensions and starts the appropriate language
 * server instance.
 *
 * @param workingDir The working directory where the language servers will be started.
 */
class LanguageAnalysisService(private val workingDir: File) {
    private val logger = LoggerFactory.getLogger(LanguageAnalysisService::class.java)
    private val languageServers = ConcurrentHashMap<String, LanguageServerInstance>()

    fun parseFile(file: File): JsonObject {
        val language = detectLanguage(file.extension)
        val server = getOrCreateServer(language)
        return server.analyzeFile(file)
    }

    private fun getOrCreateServer(language: String): LanguageServerInstance {
        return languageServers.computeIfAbsent(language) {
            logger.info("Starting LSP server for language: $language")
            createLanguageServer(language)
        }
    }

    private fun createLanguageServer(language: String): LanguageServerInstance {
        return when (language) {
            "kotlin" -> KotlinLanguageServerInstance(workingDir)
            "java" -> JavaLanguageServerInstance(workingDir)
            "python" -> PythonLanguageServerInstance(workingDir)
            "typescript",
            "javascript" -> TypeScriptLanguageServerInstance(workingDir)

            "go" -> GoLanguageServerInstance(workingDir)
            "rust" -> RustLanguageServerInstance(workingDir)
            else -> GenericLanguageServerInstance(workingDir, language)
        }
    }

    private fun detectLanguage(extension: String): String {
        return when (extension.lowercase()) {
            "kt" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "ts",
            "js",
            "tsx",
            "jsx" -> "typescript"

            "go" -> "go"
            "rs" -> "rust"
            else -> "generic"
        }
    }

    fun shutdown() {
        languageServers.values.forEach { it.shutdown() }
        languageServers.clear()
    }
}

/**
 * Abstract class representing a language server instance. This class provides methods to start the
 * server, analyze files, and handle communication with the client.
 *
 * @param workingDir The working directory where the language server will be started.
 */
abstract class LanguageServerInstance(protected val workingDir: File) {
    protected val logger = LoggerFactory.getLogger(LanguageServerInstance::class.java)
    protected var process: Process? = null
    protected var server: LanguageServer? = null
    protected var client: LanguageClient? = null
    private var isInitialized = false
    private val executor = Executors.newCachedThreadPool()

    /** Analyzes a file and returns its AST as a JSON object */
    abstract fun analyzeFile(file: File): JsonObject

    /* * Detects the language of the file based on its extension and returns the corresponding language ID */
    abstract fun getServerCommand(): List<String>

    /**
     * Starts the language server and initializes it with the given parameters. This method also sets
     * up the client to handle messages from the server.
     *
     * @return The started language server instance.
     */
    protected fun startServer(): LanguageServer {
        if (server != null && isInitialized) {
            return server!!
        }

        val command = getServerCommand()
        logger.info("Starting language server with command: $command")

        process = ProcessBuilder(command).directory(workingDir).redirectErrorStream(true).start()

        val clientImplementation = createClient()

        val launcher =
            Launcher.Builder<LanguageServer>()
                .setLocalService(clientImplementation)
                .setRemoteInterface(LanguageServer::class.java)
                .setInput(process!!.inputStream)
                .setOutput(process!!.outputStream)
                .setExecutorService(executor)
                .create()

        client = clientImplementation
        server = launcher.remoteProxy
        launcher.startListening()

        // Initialize the server
        val initParams =
            InitializeParams().apply {
                workspaceFolders = listOf(WorkspaceFolder(workingDir.toURI().toString(), workingDir.name))
                capabilities =
                    ClientCapabilities().apply {
                        textDocument =
                            TextDocumentClientCapabilities().apply {
                                synchronization = SynchronizationCapabilities().apply { dynamicRegistration = true }
                                completion = CompletionCapabilities().apply { dynamicRegistration = true }
                                hover = HoverCapabilities().apply { dynamicRegistration = true }
                                documentSymbol =
                                    DocumentSymbolCapabilities().apply {
                                        dynamicRegistration = true
                                        hierarchicalDocumentSymbolSupport = true
                                    }
                            }
                        workspace = WorkspaceClientCapabilities().apply { workspaceFolders = true }
                    }
                processId = ProcessHandle.current().pid().toInt()
                workspaceFolders =
                    listOf(WorkspaceFolder(workingDir.toURI().toString(), "${workingDir.name}"))
            }

        try {
            val initResult = server!!.initialize(initParams).get(30, TimeUnit.SECONDS)
            server!!.initialized(InitializedParams())
            isInitialized = true
            logger.info("Language server initialized successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize language server", e)
            shutdown()
            throw RuntimeException("Failed to initialize language server: ${e.message}", e)
        }

        return server!!
    }

    /**
     * Creates a client to handle messages from the language server. This client will log messages,
     * telemetry events, and diagnostics.
     *
     * @return The created language client.
     */
    protected fun createClient(): LanguageClient {
        return object : LanguageClient {
            override fun telemetryEvent(`object`: Any) {
                logger.debug("Telemetry event: $`object`")
            }

            override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
                logger.debug("Diagnostics for ${diagnostics.uri}: ${diagnostics.diagnostics.size} issues")
            }

            override fun showMessage(messageParams: MessageParams) {
                logger.info("LSP message: ${messageParams.message}")
            }

            override fun showMessageRequest(
                requestParams: ShowMessageRequestParams
            ): CompletableFuture<MessageActionItem?> {
                logger.info("LSP message request: ${requestParams.message}")
                return CompletableFuture.completedFuture(null)
            }

            override fun logMessage(message: MessageParams) {
                when (message.type) {
                    MessageType.Error -> logger.error("LSP: ${message.message}")
                    MessageType.Warning -> logger.warn("LSP: ${message.message}")
                    MessageType.Info -> logger.info("LSP: ${message.message}")
                    else -> logger.debug("LSP: ${message.message}")
                }
            }
        }
    }


    /** Shuts down the language server and terminates the process. This method should be called when
     * the server is no longer needed. */
    fun shutdown() {
        try {
            server?.shutdown()?.get(5, TimeUnit.SECONDS)
            server?.exit()
        } catch (e: Exception) {
            logger.warn("Error shutting down language server", e)
        }

        try {
            process?.destroy()
            if (process?.waitFor(5, TimeUnit.SECONDS) == false) {
                process?.destroyForcibly()
            }
        } catch (e: Exception) {
            logger.warn("Error terminating language server process", e)
        }

        executor.shutdownNow()
    }

    /**
     * Creates a TextDocumentItem from a file. This item contains the URI, language ID, text content,
     * and version of the file.
     *
     * @param file The file to create the TextDocumentItem from.
     * @return The created TextDocumentItem.
     */
    protected fun getTextDocument(file: File): TextDocumentItem {
        return TextDocumentItem().apply {
            uri = file.toURI().toString()
            languageId = detectLanguageId(file.extension)
            text = file.readText()
            version = 1
        }
    }

    /**
     * Detects the language ID based on the file extension. This method maps common file extensions
     * to their corresponding language IDs.
     *
     * @param extension The file extension to detect the language ID for.
     * @return The detected language ID.
     */
    protected fun detectLanguageId(extension: String): String {
        return when (extension.lowercase()) {
            "kt" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "ts" -> "typescript"
            "js" -> "javascript"
            "tsx" -> "typescriptreact"
            "jsx" -> "javascriptreact"
            "go" -> "go"
            "rs" -> "rust"
            else -> "plaintext"
        }
    }
}
