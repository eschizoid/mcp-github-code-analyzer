plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" }

rootProject.name = "mcp-github-code-analyzer"

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlin-mcp-sdk/sdk")
  }
}
