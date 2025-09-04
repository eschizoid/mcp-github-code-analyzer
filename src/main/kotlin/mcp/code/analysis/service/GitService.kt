package mcp.code.analysis.service

import java.io.File
import java.nio.file.Files
import mcp.code.analysis.config.AppConfig
import org.eclipse.jgit.api.Git

/** Service for interacting with Git repositories. */
open class GitService(private val config: AppConfig? = AppConfig.fromEnv()) {

  /**
   * Clones a Git repository to a temporary directory.
   *
   * Note: When this class is mocked in tests on newer JVMs, constructor interception may be bypassed.
   * To keep behavior robust under mocking, we fallback to AppConfig.fromEnv() if `config` is null.
   *
   * @param repoUrl The URL of the Git repository to clone.
   * @param branch The branch of the repository to clone.
   * @return The path to the cloned repository.
   */
  open fun cloneRepository(repoUrl: String, branch: String): File {
    val effectiveConfig = config ?: AppConfig.fromEnv()
    val workDir = File(effectiveConfig.cloneDirectory)
    val repoName = extractRepoName(repoUrl)
    val targetDir = Files.createTempDirectory(workDir.toPath(), repoName).toFile()
    Git.cloneRepository().setURI(repoUrl).setDirectory(targetDir).setBranch(branch).call().use { git ->
      git.checkout().setName(branch).call()
    }
    return targetDir
  }

  private fun extractRepoName(repoUrl: String): String = repoUrl.substringAfterLast("/").removeSuffix(".git")
}
