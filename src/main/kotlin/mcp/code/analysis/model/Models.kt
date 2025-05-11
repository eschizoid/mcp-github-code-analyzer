package mcp.code.analysis.model

import kotlinx.serialization.Serializable

@Serializable
data class FileInfo(
    val path: String,
    val size: Long,
    val extension: String?,
    val lines: Int? = null,
    val imports: List<String>? = null,
    val classes: List<String>? = null,
    val functions: List<String>? = null
)

@Serializable
data class DirectoryInfo(
    val path: String,
    val files: List<FileInfo>,
    val subdirectories: List<DirectoryInfo>
)

@Serializable
data class RepositoryStructure(
    val repositoryUrl: String,
    val branch: String,
    val rootDirectory: DirectoryInfo
)
