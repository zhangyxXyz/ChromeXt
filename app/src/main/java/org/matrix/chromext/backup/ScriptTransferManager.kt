package org.matrix.chromext.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.matrix.chromext.bridge.BrowserBridgeService

data class ScriptTransferFile(
    val name: String,
    val modifiedAt: Long,
    val size: Long,
    internal val uri: Uri,
    val scriptCount: Int = 0,
)

data class ScriptImportResult(val imported: Int, val failed: Int)

class ScriptTransferManager(private val context: Context) {
  private val backupManager = BackupManager(context)

  fun hasDirectory(): Boolean = !backupManager.settings().localTreeUri.isNullOrBlank()

  fun setDirectory(uri: Uri) = backupManager.setLocalTree(uri)

  suspend fun export(browserPackage: String): ScriptTransferFile =
      withContext(Dispatchers.IO) {
        val tree = requireTree()
        val raw = BrowserBridgeService.Registry.request(browserPackage, "exportBundle")
        val bundle = JSONObject(raw).apply {
          require(optString("type") == "ChromeXtUserScriptBackup") {
            "浏览器返回的脚本数据无效"
          }
        }
        val name =
            "ChromeXt-userscripts-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.json"
        val document =
            tree.createFile("application/json", name) ?: error("无法在所选目录创建导出文件")
        context.contentResolver.openOutputStream(document.uri, "wt")?.bufferedWriter()?.use {
          it.write(raw)
        } ?: error("无法写入导出文件")
        ScriptTransferFile(
            document.name ?: name,
            document.lastModified(),
            document.length(),
            document.uri,
            bundle.optJSONArray("scripts")?.length() ?: 0,
        )
      }

  suspend fun files(): List<ScriptTransferFile> =
      withContext(Dispatchers.IO) {
        requireTree()
            .listFiles()
            .mapNotNull { document ->
              val name = document.name ?: return@mapNotNull null
              if (!document.isFile ||
                  !name.startsWith("ChromeXt-userscripts-") ||
                  !name.endsWith(".json", ignoreCase = true)) {
                return@mapNotNull null
              }
              ScriptTransferFile(name, document.lastModified(), document.length(), document.uri)
            }
            .sortedByDescending(ScriptTransferFile::modifiedAt)
      }

  suspend fun import(browserPackage: String, file: ScriptTransferFile): ScriptImportResult =
      withContext(Dispatchers.IO) {
        val staged = java.io.File(context.cacheDir, "script-import-${System.nanoTime()}.json")
        try {
          context.contentResolver.openInputStream(file.uri)?.use { input ->
            staged.outputStream().buffered().use(input::copyTo)
          } ?: error("无法读取脚本导入文件")
          val result =
              JSONObject(
                  BrowserBridgeService.Registry.requestFile(
                      browserPackage,
                      "importBundle",
                      staged,
                  ))
          result.optString("error").takeIf(String::isNotBlank)?.let { error(it) }
          ScriptImportResult(result.optInt("imported"), result.optInt("failed"))
        } finally {
          staged.delete()
        }
      }

  private fun requireTree(): DocumentFile {
    val uri = backupManager.settings().localTreeUri ?: error("请先设置脚本数据目录")
    return DocumentFile.fromTreeUri(context, Uri.parse(uri))
        ?.takeIf(DocumentFile::isDirectory)
        ?: error("脚本数据目录不可用，请重新设置")
  }
}
