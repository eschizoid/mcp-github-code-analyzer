import mcp.code.analysis.server.Server
import org.slf4j.LoggerFactory

/**
 * Start sse-server mcp on port 3001.
 *
 * @param args
 * - "--stdio": Runs an MCP server using standard input/output.
 * - "--sse-server-ktor <port>": Runs an SSE MCP server using Ktor plugin (default if no argument is
 *   provided).
 * - "--sse-server <port>": Runs an SSE MCP server with a plain configuration.
 */
fun main(args: Array<String>) {
  val logger = LoggerFactory.getLogger("Main")
  val server = Server()

  val command = args.firstOrNull() ?: "--sse-server-ktor"
  val port = args.getOrNull(1)?.toIntOrNull() ?: 3001

  when (command) {
    "--stdio" -> server.runMcpServerUsingStdio()
    "--sse-server-ktor" -> server.runSseMcpServerUsingKtorPlugin(port)
    "--sse-server" -> server.runSseMcpServerWithPlainConfiguration(port)
    else -> {
      logger.error("Unknown command: $command")
    }
  }
}
