package org.matrix.chromext.backup

import androidx.documentfile.provider.DocumentFile

internal fun browserDirectoryName(browserPackage: String): String =
    browserPackage
        .trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-')
        .ifBlank { "browser" }

internal fun DocumentFile.findBrowserDirectory(browserPackage: String): DocumentFile? =
    findFile(browserDirectoryName(browserPackage))?.takeIf(DocumentFile::isDirectory)

internal fun DocumentFile.requireBrowserDirectory(browserPackage: String): DocumentFile {
  val name = browserDirectoryName(browserPackage)
  findFile(name)?.let { existing ->
    require(existing.isDirectory) { "目标浏览器目录不可用：$name" }
    return existing
  }
  return createDirectory(name) ?: error("无法创建目标浏览器目录：$name")
}

internal fun WebDavConfig.forBrowser(browserPackage: String): WebDavConfig =
    copy(
        directory =
            listOf(directory.trim().trim('/'), browserDirectoryName(browserPackage))
                .filter(String::isNotBlank)
                .joinToString("/"))
