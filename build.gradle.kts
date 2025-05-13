plugins {
  kotlin("jvm") version "2.1.0"
  kotlin("plugin.serialization") version "2.1.0"
  id("com.diffplug.spotless") version "7.0.3"
  application
}

group = "mcp.code.analysis"

version = "1.0-SNAPSHOT"

dependencies {
  // Kotlin standard library
  implementation(kotlin("stdlib"))
  implementation("io.ktor:ktor-server-sse:3.1.3")

  // Ktor server
  val ktorVersion = "3.1.3"
  implementation("io.ktor:ktor-server-core:$ktorVersion")
  implementation("io.ktor:ktor-server-netty:$ktorVersion")
  implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
  implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
  implementation("io.ktor:ktor-server-openapi:$ktorVersion")

  // MCP SDK
  implementation("io.modelcontextprotocol:kotlin-sdk:0.5.0")

  // Swagger
  implementation("io.swagger.core.v3:swagger-core:2.2.20")

  // Ktor client for GitHub API
  implementation("io.ktor:ktor-client-core:$ktorVersion")
  implementation("io.ktor:ktor-client-cio:$ktorVersion")
  implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

  // Logging
  implementation("ch.qos.logback:logback-classic:1.5.18")

  // Coroutines
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

  // Serialization
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

  // JGit for repository interaction
  implementation("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r")

  // Testing
  testImplementation(kotlin("test"))
}

application { mainClass.set("ServerKt") }

tasks.jar {
  manifest { attributes["Main-Class"] = "ServerKt" }

  duplicatesStrategy = DuplicatesStrategy.EXCLUDE

  exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

  from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(23) }

spotless {
  kotlin {
    ktfmt().googleStyle()
    toggleOffOn()
    target("**/*.kt")
    trimTrailingWhitespace()
    endWithNewline()
  }

  kotlinGradle {
    ktfmt().googleStyle()
    target("*.gradle.kts")
  }
}
