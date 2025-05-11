package mcp.code.analysis.service

import java.io.File
import java.nio.file.Files
import mcp.code.analysis.config.AppConfig
import org.eclipse.jgit.api.Git

/** Service for interacting with Git repositories. */
class GitService {
  private val config = AppConfig()

  fun cloneRepository(repoUrl: String, branch: String): File {
    val workDir = File(config.workingDirectory)
    if (!workDir.exists()) {
      workDir.mkdirs()
    }

    val repoName = extractRepoName(repoUrl)
    val targetDir = Files.createTempDirectory(workDir.toPath(), repoName).toFile()

    Git.cloneRepository().setURI(repoUrl).setDirectory(targetDir).setBranch(branch).call().use { git
      ->
      git.checkout().setName(branch).call()
    }

    return targetDir
  }

  private fun extractRepoName(repoUrl: String): String =
    repoUrl.substringAfterLast("/").removeSuffix(".git")
}
