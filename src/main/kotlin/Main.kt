import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import mcp.code.analysis.config.AppConfig
import mcp.code.analysis.routes.registerRoutes

fun main() {
    val config = AppConfig()

    embeddedServer(Netty, port = config.serverPort) {
        install(ContentNegotiation) {
            json()
        }

        registerRoutes(this)

    }.start(wait = true)
}