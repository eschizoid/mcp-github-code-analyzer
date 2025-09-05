import org.jreleaser.model.Active.ALWAYS
import org.jreleaser.model.Active.NEVER

plugins {
  java
  application
  `maven-publish`
  signing
  jacoco
  kotlin("jvm") version "2.2.10"
  kotlin("plugin.serialization") version "2.2.10"
  id("com.diffplug.spotless") version "7.2.1"
  id("io.kotest.multiplatform") version "5.9.1"
  id("org.jreleaser") version "1.19.0"
  id("pl.allegro.tech.build.axion-release") version "1.20.1"
}

scmVersion {
  unshallowRepoOnCI.set(true)
  tag { prefix.set("v") }
  versionCreator("versionWithBranch")
  branchVersionCreator.set(mapOf("main" to "simple"))
  val incrementType =
    when (project.findProperty("release.incrementer")?.toString()) {
      "patch" -> "incrementPatch"
      "minor" -> "incrementMinor"
      "major" -> "incrementMajor"
      else -> "incrementMinor"
    }
  versionIncrementer(incrementType)
  branchVersionIncrementer.set(mapOf("feature/.*" to "incrementMinor", "bugfix/.*" to "incrementPatch"))
}

group = "io.github.eschizoid"

version = rootProject.scmVersion.version

description = "MCP Server for GitHub Code Repositories Analysis"

buildscript {
  configurations.all { resolutionStrategy { force("org.eclipse.jgit:org.eclipse.jgit:6.10.1.202505221210-r") } }
}

dependencies {
  val ktorVersion = "3.2.0"
  val coroutinesVersion = "1.10.2"
  val kotestVersion = "5.9.1"
  val mcpKotlinSdk = "0.6.0"

  // Kotlin standard library
  implementation(kotlin("stdlib"))

  // Kotlin
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
  implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.0")

  // Ktor server
  implementation("io.ktor:ktor-client-cio:$ktorVersion")
  implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
  implementation("io.ktor:ktor-client-core:$ktorVersion")
  implementation("io.ktor:ktor-server-core:$ktorVersion")
  implementation("io.ktor:ktor-server-netty:$ktorVersion")
  implementation("io.ktor:ktor-server-sse:$ktorVersion")
  implementation("io.ktor:ktor-server-cors:${ktorVersion}")
  implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
  implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

  // MCP SDK
  implementation("io.modelcontextprotocol:kotlin-sdk:$mcpKotlinSdk")

  // Logging
  implementation("ch.qos.logback:logback-classic:1.5.18")

  // JGit for repository interaction
  implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.1.202505221210-r")

  implementation("io.github.tree-sitter:ktreesitter-jvm:0.24.1")

  // Testing
  testImplementation(kotlin("test"))
  testImplementation("io.mockk:mockk:1.14.2")
  testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")

  testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
  testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
  testImplementation("io.kotest:kotest-property:$kotestVersion")
}

application { mainClass.set("MainKt") }

tasks.jar {
  manifest { attributes["Main-Class"] = "MainKt" }
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
  from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.test {
  useJUnitPlatform()
  jvmArgs("-Dnet.bytebuddy.experimental=true")
}

tasks.jacocoTestReport {
  reports {
    csv.required.set(true)
    xml.required.set(true)
    html.required.set(true)
  }
}

java {
  withSourcesJar()
  withJavadocJar()
  toolchain { languageVersion = JavaLanguageVersion.of(24) }
}

repositories {
  mavenCentral()
  maven("https://maven.pkg.jetbrains.space/public/p/kotlin-mcp-sdk/sdk")
  maven {
    credentials {
      username =
        System.getenv("JRELEASER_MAVENCENTRAL_SONATYPE_USERNAME")
          ?: project.properties["mavencentralSonatypeUsername"]?.toString()
      password =
        System.getenv("JRELEASER_MAVENCENTRAL_SONATYPE_PASSWORD")
          ?: project.properties["mavencentralSonatypePassword"]?.toString()
    }
    url = uri("https://central.sonatype.com/")
  }
}

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

signing {
  afterEvaluate { sign(publishing.publications["maven"]) }

  val signingKey = System.getenv("JRELEASER_GPG_SECRET_KEY") ?: project.properties["signing.secretKey"]?.toString()
  val signingPassword = System.getenv("JRELEASER_GPG_PASSPHRASE") ?: project.properties["signing.password"]?.toString()

  if (signingKey != null && signingPassword != null) {
    useInMemoryPgpKeys(signingKey, signingPassword)
  }
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      groupId = "io.github.eschizoid"
      artifactId = "mcp-github-code-analyzer"
      from(components["java"])

      pom {
        name.set("mcp-github-code-analyzer")
        description.set("MCP Server for GitHub Code Repositories Analysis")
        url.set("https://github.com/eschizoid/mcp-github-code-analyzer")
        inceptionYear.set("2025")

        licenses {
          license {
            name.set("Apache License 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0")
          }
        }

        developers {
          developer {
            id.set("eschizoid")
            name.set("Mariano Gonzalez")
            email.set("mariano.gonzalez.mx@gmail.com")
          }
        }

        scm {
          connection.set("scm:git:git://github.com/eschizoid/mcp-github-code-analyzer.git")
          developerConnection.set("scm:git:ssh://github.com/eschizoid/mcp-github-code-analyzer.git")
          url.set("https://github.com/eschizoid/mcp-github-code-analyzer")
        }
      }
    }
  }

  repositories { maven { url = uri(layout.buildDirectory.dir("staging-deploy")) } }
}

jreleaser {
  project {
    name.set("mcp-github-code-analyzer")
    description.set("MCP Server for GitHub Code Repositories Analysis")
    authors.set(listOf("Mariano Gonzalez"))
    license.set("Apache-2.0")
    links { homepage.set("https://github.com/eschizoid/mcp-github-code-analyzer") }
    inceptionYear.set("2025")
    tags.set(listOf("MCP", "LLM", "Ollama", "kotlin", "github", "code analysis"))
  }

  signing { active.set(NEVER) }

  deploy {
    maven {
      mavenCentral {
        create("sonatype") {
          active.set(ALWAYS)
          url.set("https://central.sonatype.com/api/v1/publisher")
          stagingRepository("build/staging-deploy")
          enabled.set(true)
          sign.set(false)
          maxRetries.set(180)
        }
      }
    }
  }

  release {
    github {
      enabled.set(true)
      overwrite.set(false)
    }
  }
}
