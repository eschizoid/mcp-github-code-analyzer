import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import mcp.code.analysis.config.AppConfig
import mcp.code.analysis.server.Server
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Main entry point for the MCP GitHub Code Analysis Server application.
 *
 * This application provides a server that analyzes GitHub repositories using the Model Context Protocol (MCP). It can
 * operate in different modes: standard I/O mode or Server-Sent Events (SSE) mode using either plain configuration or
 * Ktor plugin.
 *
 * @param args Command-line arguments to determine server behavior:
 *     - "--stdio": Runs an MCP server using standard input/output.
 *     - "--sse-server-ktor <port>": Runs an SSE MCP server using Ktor plugin (default if no args provided).
 *     - "--sse-server <port>": Runs an SSE MCP server with plain configuration. If no port is specified, the value from
 *       environment variable SERVER_PORT or default 3001 is used.
 */
fun main(args: Array<String>) {
  val logger = LoggerFactory.getLogger("Main")

  runApplication(args)
    .onSuccess { logger.info("Application terminated successfully") }
    .onFailure { error -> logger.error("Application failed: ${error.message}", error) }
}

/**
 * Data class representing application command settings.
 *
 * @property type The type of server to start (STDIO, SSE_KTOR, SSE_PLAIN).
 * @property port The port number on which the server will listen (for SSE modes only).
 */
data class AppCommand(val type: CommandType, val port: Int)

/**
 * Enum representing the application command type.
 * * STDIO - Standard input/output mode for the MCP server
 * * SSE_KTOR - Server-Sent Events mode using the Ktor plugin
 * * SSE_PLAIN - Server-Sent Events mode using plain configuration
 * * UNKNOWN - Unknown or unsupported command
 */
enum class CommandType {
  STDIO,
  SSE_KTOR,
  SSE_PLAIN,
  UNKNOWN;

  /**
   * Converts a command-line argument to the corresponding CommandType.
   *
   * @param arg The command-line argument string.
   * @return The CommandType corresponding to the argument, or SSE_KTOR if null, or UNKNOWN if the argument is not
   *   recognized.
   */
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

/**
 * Runs the application by parsing arguments, ensuring the necessary directories exist, and executing the appropriate
 * server command.
 *
 * @param args Command-line arguments to determine server behavior.
 * @return Result wrapping Unit, with success if the application runs successfully, or failure with the exception if an
 *   error occurs.
 * @throws IOException If the required directories cannot be created.
 */
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

/**
 * Parses command-line arguments into an [AppCommand] object.
 *
 * @param args The command-line arguments array.
 * @param defaultPort The default port to use if not specified in arguments.
 * @return An AppCommand object containing the parsed command type and port.
 */
fun parseArgs(args: Array<String>, defaultPort: Int): AppCommand {
  val commandType = CommandType.fromArg(args.firstOrNull())
  val port = args.getOrNull(1)?.toIntOrNull() ?: defaultPort
  return AppCommand(commandType, port)
}

/**
 * Ensures that all required application directories exist, creating them if necessary.
 *
 * @param config The application configuration containing directory paths.
 * @return A list of Results, each containing a Pair of the Path and a Boolean indicating whether the directory was
 *   newly created (true) or already existed (false).
 */
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

/**
 * Creates a directory and all parent directories if they don't exist.
 *
 * @param path The Path to the directory to create.
 * @return Result containing a Pair of the Path and a Boolean indicating whether the directory was newly created (true)
 *   or already existed (false).
 * @throws IOException If the path exists but is not a directory, creation fails, or the directory doesn't exist after
 *   attempted creation.
 */
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

/**
 * Executes the appropriate server command based on the provided AppCommand.
 *
 * @param command The AppCommand specifying the type of server to start and its port.
 * @param server The Server instance to use for execution.
 * @throws IllegalArgumentException If the command type is UNKNOWN.
 */
fun executeCommand(command: AppCommand, server: Server) {
  val logger = LoggerFactory.getLogger("Main")

  when (command.type) {
    CommandType.STDIO -> server.runMcpServerUsingStdio()
    CommandType.SSE_KTOR -> server.runSseMcpServerUsingKtorPlugin(command.port)
    CommandType.SSE_PLAIN -> server.runSseMcpServerWithPlainConfiguration(command.port)
    CommandType.UNKNOWN -> throw IllegalArgumentException("Unknown command: ${command.type}")
  }.also { logger.info("Executed command: ${command.type}") }
}
