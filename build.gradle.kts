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

  // Ktor server
  val ktorVersion = "3.1.3"
  implementation("io.ktor:ktor-client-cio:$ktorVersion")
  implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
  implementation("io.ktor:ktor-client-core:$ktorVersion")
  implementation("io.ktor:ktor-server-core:$ktorVersion")
  implementation("io.ktor:ktor-server-netty:$ktorVersion")
  implementation("io.ktor:ktor-server-sse:$ktorVersion")
  implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
  implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

  // MCP SDK
  implementation("io.modelcontextprotocol:kotlin-sdk:0.5.0")

  // Logging
  implementation("ch.qos.logback:logback-classic:1.5.18")

  // Coroutines
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

  // Serialization
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

  // JGit for repository interaction
  implementation("org.eclipse.jgit:org.eclipse.jgit:7.2.1.202505142326-r")

  // Testing
  testImplementation(kotlin("test"))
}

application { mainClass.set("MainKt") }

tasks.jar {
  manifest { attributes["Main-Class"] = "MainKt" }

  duplicatesStrategy = DuplicatesStrategy.EXCLUDE

  exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

  from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(23) }

spotless {
  kotlin {
    ktfmt().googleStyle().configure {
      it.setMaxWidth(120)
      it.setRemoveUnusedImports(true)
    }
    toggleOffOn()
    target("**/*.kt")
    trimTrailingWhitespace()
    endWithNewline()
  }

  kotlinGradle {
    ktfmt().googleStyle().configure {
      it.setMaxWidth(120)
      it.setRemoveUnusedImports(true)
    }
    target("*.gradle.kts")
  }
}
