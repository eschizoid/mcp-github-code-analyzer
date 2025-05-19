import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import mcp.code.analysis.config.AppConfig
import mcp.code.analysis.server.Server
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler

/**
 * Start sse-server mcp on port 3001.
 *
 * @param args
 * - "--stdio": Runs an MCP server using standard input/output.
 * - "--sse-server-ktor <port>": Runs an SSE MCP server using Ktor plugin (default if no argument is provided).
 * - "--sse-server <port>": Runs an SSE MCP server with a plain configuration.
 */
fun main(args: Array<String>) {
  SLF4JBridgeHandler.removeHandlersForRootLogger()
  SLF4JBridgeHandler.install()

  val logger = LoggerFactory.getLogger("Main")

  runApplication(args)
    .onSuccess { logger.info("Application terminated successfully") }
    .onFailure { error -> logger.error("Application failed: ${error.message}", error) }
}

/** Data class representing application command settings */
data class AppCommand(val type: CommandType, val port: Int)

/** Enum representing possible commands */
enum class CommandType {
  STDIO,
  SSE_KTOR,
  SSE_PLAIN,
  UNKNOWN;

  companion object {
    fun fromArg(arg: String?): CommandType =
      when (arg) {
        "--stdio" -> STDIO
        "--sse-server-ktor" -> SSE_KTOR
        "--sse-server" -> SSE_PLAIN
        null -> SSE_KTOR
        else -> UNKNOWN
      }
  }
}

/** Runs the application with a functional approach */
fun runApplication(args: Array<String>): Result<Unit> = runCatching {
  val logger = LoggerFactory.getLogger("Main")

  val config = AppConfig.fromEnv()
  val command = parseArgs(args, config.serverPort)
  val directoryResults = ensureApplicationDirectories(config)
  val allDirectoriesCreated = directoryResults.all { it.isSuccess }

  if (!allDirectoriesCreated) {
    logger.error("Failed to create necessary directories, check previous errors")
    throw IOException("Failed to create one or more required directories")
  }
  executeCommand(command, Server())
}

/** Parse command line arguments into a structured command */
fun parseArgs(args: Array<String>, defaultPort: Int): AppCommand {
  val commandType = CommandType.fromArg(args.firstOrNull())
  val port = args.getOrNull(1)?.toIntOrNull() ?: defaultPort
  return AppCommand(commandType, port)
}

/** Ensures both application directories are properly created with clear error handling */
fun ensureApplicationDirectories(config: AppConfig): List<Result<Pair<Path, Boolean>>> {
  val logger = LoggerFactory.getLogger("Main")

  val cloneDirResult = createDirectoryWithFullPath(Paths.get(config.cloneDirectory))
  val logsDirResult = createDirectoryWithFullPath(Paths.get(config.logDirectory))

  listOf(cloneDirResult, logsDirResult).forEach { result ->
    result.fold(
      onSuccess = { (path, created) ->
        if (created) logger.info("Created directory: $path") else logger.debug("Directory already exists: $path")
      },
      onFailure = { error -> logger.error("Failed to create directory: ${error.message}", error) },
    )
  }
  return listOf(cloneDirResult, logsDirResult)
}

/** Creates a directory with its full path, handling all parent directories */
fun createDirectoryWithFullPath(path: Path): Result<Pair<Path, Boolean>> = runCatching {
  val logger: Logger = LoggerFactory.getLogger("Main")

  val directory = path.toFile()
  if (directory.exists()) {
    if (!directory.isDirectory) throw IOException("Path exists but is not a directory: $path")
    return@runCatching path to false
  }
  val created = directory.mkdirs()
  if (!created) throw IOException("Failed to create directory: $path")
  if (!directory.exists() || !directory.isDirectory)
    throw IOException("Directory creation appeared to succeed but directory does not exist: $path")
  logger.info("Created directory at ${directory.absolutePath}")
  path to true
}

/** Executes the appropriate server command */
fun executeCommand(command: AppCommand, server: Server) {
  val logger = LoggerFactory.getLogger("Main")

  when (command.type) {
    CommandType.STDIO -> server.runMcpServerUsingStdio()
    CommandType.SSE_KTOR -> server.runSseMcpServerUsingKtorPlugin(command.port)
    CommandType.SSE_PLAIN -> server.runSseMcpServerWithPlainConfiguration(command.port)
    CommandType.UNKNOWN -> throw IllegalArgumentException("Unknown command: ${command.type}")
  }.also { logger.info("Executed command: ${command.type}") }
}
