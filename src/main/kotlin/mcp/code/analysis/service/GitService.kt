package mcp.code.analysis.service

import java.io.File
import java.nio.file.Files
import mcp.code.analysis.config.AppConfig
import org.eclipse.jgit.api.Git

/**
 * GitService is responsible for cloning Git repositories and checking out branches.
 *
 * @param config The application configuration containing the working directory.
 */
class GitService {
  private val config = AppConfig()

  /**
   * Clones a Git repository and checks out the specified branch.
   *
   * @param repoUrl The URL of the Git repository to clone.
   * @param branch The branch to check out after cloning.
   * @return The directory where the repository is cloned.
   */
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
