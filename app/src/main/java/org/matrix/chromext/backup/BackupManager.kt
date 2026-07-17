package org.matrix.chromext.backup

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.BuildConfig
import org.matrix.chromext.XposedServiceRepository
import org.matrix.chromext.bridge.BrowserBridgeService

data class BackupSettings(
    val webDav: WebDavConfig,
    val localRetentionCount: Int,
    val remoteRetentionCount: Int,
    val localTreeUri: String?,
    val encryptionPassword: String,
    val includeWebDavConfig: Boolean,
)

data class LocalStorageLocation(
    val configured: Boolean,
    val displayPath: String,
    val isDefault: Boolean,
)

enum class BackupMode(val includesLocal: Boolean, val includesRemote: Boolean) {
  LOCAL_AND_REMOTE(true, true),
  LOCAL(true, false),
  REMOTE(false, true),
}

data class BackupResult(
    val localFile: File?,
    val remoteUploaded: Boolean,
    val remoteError: String? = null,
)

data class LocalBackup(
    val name: String,
    val modifiedAt: Long,
    val size: Long,
    internal val documentUri: Uri? = null,
    internal val internalFile: File? = null,
)

class BackupManager(private val context: Context) {
  private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private val backupDirectory = File(context.filesDir, "backups")

  fun settings(): BackupSettings =
      BackupSettings(
          webDav =
              WebDavConfig(
                  prefs.getString(KEY_URL, "").orEmpty(),
                  prefs.getString(KEY_USER, "").orEmpty(),
                  prefs.getString(KEY_PASSWORD, "").orEmpty(),
                  prefs.getString(KEY_DIRECTORY, "ChromeXt").orEmpty().ifBlank { "ChromeXt" },
                  prefs.getString(KEY_DEVICE_NAME, Build.MODEL).orEmpty().ifBlank { Build.MODEL },
              ),
          localRetentionCount =
              prefs.getInt(KEY_LOCAL_RETENTION, 5).takeIf { it in RETENTION_VALUES } ?: 5,
          remoteRetentionCount =
              prefs.getInt(KEY_REMOTE_RETENTION, 5).takeIf { it in RETENTION_VALUES } ?: 5,
          localTreeUri = prefs.getString(KEY_TREE_URI, null),
          encryptionPassword = prefs.getString(KEY_ENCRYPTION_PASSWORD, "").orEmpty(),
          includeWebDavConfig = prefs.getBoolean(KEY_INCLUDE_WEBDAV_CONFIG, false),
      )

  fun localStorageLocation(): LocalStorageLocation {
    val treeUri = settings().localTreeUri
    val path = treeUri?.let(::displayLocalPath) ?: DEFAULT_LOCAL_PATH
    return LocalStorageLocation(
        configured = !treeUri.isNullOrBlank(),
        displayPath = path,
        isDefault = normalizeLocalPath(path) == normalizeLocalPath(DEFAULT_LOCAL_PATH),
    )
  }

  fun saveSettings(settings: BackupSettings) {
    prefs
        .edit()
        .putString(KEY_URL, settings.webDav.url.trim())
        .putString(KEY_USER, settings.webDav.username.trim())
        .putString(KEY_PASSWORD, settings.webDav.password)
        .putString(KEY_DIRECTORY, settings.webDav.directory.trim().trim('/'))
        .putString(KEY_DEVICE_NAME, settings.webDav.deviceName.trim())
        .putInt(KEY_LOCAL_RETENTION, settings.localRetentionCount)
        .putInt(KEY_REMOTE_RETENTION, settings.remoteRetentionCount)
        .putString(KEY_TREE_URI, settings.localTreeUri)
        .putString(KEY_ENCRYPTION_PASSWORD, settings.encryptionPassword)
        .putBoolean(KEY_INCLUDE_WEBDAV_CONFIG, settings.includeWebDavConfig)
        .apply()
  }

  fun setLocalTree(uri: Uri) {
    context.contentResolver.takePersistableUriPermission(
        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    saveSettings(settings().copy(localTreeUri = uri.toString()))
  }

  suspend fun createBackup(
      browserPackage: String,
      mode: BackupMode = BackupMode.LOCAL_AND_REMOTE,
  ): BackupResult =
      withContext(Dispatchers.IO) {
        val config = settings()
        val deviceName =
            config.webDav.deviceName
                .trim()
                .ifBlank { Build.MODEL }
                .replace(Regex("[^\\p{L}\\p{N}._-]+"), "-")
                .trim('-')
                .ifBlank { "Android" }
        val browserId = browserDirectoryName(browserPackage)
        val name =
            "${WebDavClient.BACKUP_PREFIX}$deviceName-${LocalDateTime.now().format(FILE_TIME)}.zip"
        if (mode.includesLocal) {
          require(!config.localTreeUri.isNullOrBlank()) { "请先选择本地备份目录" }
        }
        val snapshot =
            BrowserBridgeService.Registry.exportSnapshot(
                browserPackage, File(context.cacheDir, "browser-snapshot.json"))
        val manifest =
            JSONObject().apply {
              put("type", "ChromeXtBackup")
              put("version", 1)
              put("appVersion", BuildConfig.VERSION_NAME)
              put("versionCode", BuildConfig.VERSION_CODE)
              put("browserPackage", browserPackage)
              put("createdAt", System.currentTimeMillis())
            }
        val inputs =
            mutableListOf<BackupInput>(
                BackupInput.Serialized(MANIFEST_ENTRY, manifest.toString(2)),
                BackupInput.IntermediateFile(BROWSER_SNAPSHOT_ENTRY, snapshot),
                BackupInput.Serialized(
                    UI_SETTINGS_ENTRY,
                    encodePreferences(context.getSharedPreferences("ChromeXtUi", Context.MODE_PRIVATE))
                        .toString()),
                BackupInput.Serialized(
                    RUNTIME_SETTINGS_ENTRY,
                    encodePreferences(
                            XposedServiceRepository.settings
                                ?: context.getSharedPreferences("ChromeXt", Context.MODE_PRIVATE))
                        .toString()),
            )
        if (config.includeWebDavConfig) inputs += webDavConfigInput(config.webDav)
        val archive =
            BackupArchive.pack(
                inputs,
                File(
                    if (mode.includesLocal) File(backupDirectory, browserId)
                    else File(context.cacheDir, "backups/$browserId"),
                    name,
                ),
                config.encryptionPassword,
            )
        var localFile: File? = null
        if (mode.includesLocal) {
          copyToTree(
              archive,
              Uri.parse(config.localTreeUri),
              browserPackage,
              config.localRetentionCount,
          )
          pruneInternal(browserPackage, config.localRetentionCount)
          localFile = archive
        }
        var remoteUploaded = false
        var remoteError: String? = null
        if (mode.includesRemote) {
          if (!config.webDav.isConfigured) {
            remoteError = "WebDAV 配置不完整"
          } else {
            runCatching {
                  WebDavClient(config.webDav.forBrowser(browserPackage)).apply {
                    upload(archive)
                    prune(config.remoteRetentionCount)
                  }
                }
                .onSuccess { remoteUploaded = true }
                .onFailure { remoteError = it.localizedMessage ?: "WebDAV 备份失败" }
          }
        }
        snapshot.delete()
        if (!mode.includesLocal) archive.delete()
        BackupResult(localFile, remoteUploaded, remoteError)
      }

  suspend fun localBackups(browserPackage: String): List<LocalBackup> =
      withContext(Dispatchers.IO) {
        val browserId = browserDirectoryName(browserPackage)
        val external =
            settings().localTreeUri?.let { treeUri ->
              val root =
                  DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
                      ?: error("本地备份目录不可用，请重新选择")
              val current =
                  root.findBrowserDirectory(browserPackage)
                      ?.listFiles()
                      .orEmpty()
                      .mapNotNull(::documentBackup)
              val legacy =
                  root.listFiles().mapNotNull { document ->
                    val name = document.name ?: return@mapNotNull null
                    if (!document.isFile || !isLegacyBackupName(name, browserId)) {
                      return@mapNotNull null
                    }
                    documentBackup(document)
                  }
              current + legacy
            }.orEmpty()
        val names = external.mapTo(hashSetOf(), LocalBackup::name)
        val currentInternal =
            File(backupDirectory, browserId)
                .listFiles { file -> file.isFile && isBackupName(file.name) }
                ?.filterNot { it.name in names }
                ?.map { LocalBackup(it.name, it.lastModified(), it.length(), internalFile = it) }
                .orEmpty()
        val currentNames = (names + currentInternal.map(LocalBackup::name)).toHashSet()
        val legacyInternal =
            backupDirectory.listFiles { file ->
              file.isFile && isLegacyBackupName(file.name, browserId)
            }
                ?.filterNot { it.name in currentNames }
                ?.map { LocalBackup(it.name, it.lastModified(), it.length(), internalFile = it) }
                .orEmpty()
        (external + currentInternal + legacyInternal).sortedByDescending(LocalBackup::modifiedAt)
      }

  suspend fun remoteBackups(browserPackage: String): List<RemoteBackup> =
      withContext(Dispatchers.IO) {
        val config = settings().webDav
        require(config.isConfigured) { "请先配置 WebDAV" }
        val current = WebDavClient(config.forBrowser(browserPackage)).listBackups()
        val browserId = browserDirectoryName(browserPackage)
        val legacy =
            WebDavClient(config).listBackups().filter { isLegacyBackupName(it.name, browserId) }
        (current + legacy).distinctBy { "${it.directory}/${it.name}" }.sortedByDescending {
          it.modifiedAt
        }
      }

  suspend fun testWebDav() =
      withContext(Dispatchers.IO) {
        val config = settings().webDav
        require(config.isConfigured) { "WebDAV 配置不完整" }
        WebDavClient(config).test()
      }

  suspend fun restore(backup: LocalBackup, targetPackage: String) {
    val restored =
        when {
          backup.documentUri != null -> unpack(copyRestoreInput(backup.documentUri))
          backup.internalFile != null -> unpack(backup.internalFile)
          else -> error("本地备份文件不可用")
        }
    applyRestore(restored, targetPackage)
  }

  suspend fun restore(uri: Uri, targetPackage: String) {
    val input = copyRestoreInput(uri)
    if (input.isJsonDocument()) {
      applyLegacyScriptRestore(input.readText(), targetPackage)
    } else {
      applyRestore(unpack(input), targetPackage)
    }
  }

  suspend fun restore(remote: RemoteBackup, targetPackage: String) {
    val config = settings()
    val remoteConfig =
        if (remote.directory.isBlank()) config.webDav.forBrowser(targetPackage)
        else config.webDav.copy(directory = remote.directory)
    val file =
        withContext(Dispatchers.IO) {
          WebDavClient(remoteConfig).download(remote.name, File(context.cacheDir, remote.name))
        }
    applyRestore(unpack(file), targetPackage)
  }

  private suspend fun applyRestore(restored: RestoredBackup, targetPackage: String) {
    val manifest = JSONObject(restored.text(MANIFEST_ENTRY) ?: error("备份缺少 manifest.json"))
    require(manifest.optString("type") == "ChromeXtBackup") { "不是 ChromeXt 完整备份" }
    require(manifest.optInt("version") == 1) { "不支持的备份版本" }
    val snapshot = restored.file(BROWSER_SNAPSHOT_ENTRY) ?: error("备份缺少浏览器数据")
    val rollback =
        BrowserBridgeService.Registry.exportSnapshot(
            targetPackage, File(context.cacheDir, "restore-rollback.json"))
    try {
      BrowserBridgeService.Registry.restoreSnapshot(targetPackage, snapshot)
      restored.text(UI_SETTINGS_ENTRY)?.let {
        restorePreferences(context.getSharedPreferences("ChromeXtUi", Context.MODE_PRIVATE), JSONObject(it))
      }
      restored.text(RUNTIME_SETTINGS_ENTRY)?.let {
        restorePreferences(
            XposedServiceRepository.settings
                ?: context.getSharedPreferences("ChromeXt", Context.MODE_PRIVATE),
            JSONObject(it))
      }
      restored.text(WEBDAV_CONFIG_ENTRY)?.let(::restoreWebDavConfig)
    } catch (error: Throwable) {
      runCatching { BrowserBridgeService.Registry.restoreSnapshot(targetPackage, rollback) }
      throw error
    } finally {
      rollback.delete()
    }
  }

  private suspend fun applyLegacyScriptRestore(raw: String, targetPackage: String) {
    val scripts = LegacyScriptBackup.parse(raw)
    val rollback =
        BrowserBridgeService.Registry.exportSnapshot(
            targetPackage, File(context.cacheDir, "legacy-restore-rollback.json"))
    try {
      scripts.forEach { script ->
        val response =
            JSONObject(
                BrowserBridgeService.Registry.request(
                    targetPackage,
                    "save",
                    JSONObject().apply {
                      put("previousId", script.id)
                      put("source", script.source)
                    }.toString(),
                ))
        response.optString("error").takeIf(String::isNotBlank)?.let(::error)
      }
    } catch (failure: Throwable) {
      runCatching { BrowserBridgeService.Registry.restoreSnapshot(targetPackage, rollback) }
      throw failure
    } finally {
      rollback.delete()
    }
  }

  private suspend fun copyRestoreInput(uri: Uri): File =
      withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "restore-input")
        context.contentResolver.openInputStream(uri)?.use { input ->
          temp.outputStream().use(input::copyTo)
        } ?: error("无法读取备份文件")
        temp
      }

  private suspend fun unpack(file: File): RestoredBackup =
      withContext(Dispatchers.IO) {
        val restored =
            BackupArchive.unpack(
                file, File(context.cacheDir, "restored-backup"), settings().encryptionPassword)
        restored
      }

  private fun webDavConfigInput(config: WebDavConfig) =
      BackupInput.Serialized(
          WEBDAV_CONFIG_ENTRY,
          JSONObject().apply {
            put("url", config.url)
            put("username", config.username)
            put("password", config.password)
            put("directory", config.directory)
            put("deviceName", config.deviceName)
          }.toString())

  private fun restoreWebDavConfig(raw: String) {
    val json = JSONObject(raw)
    val current = settings()
    saveSettings(
        current.copy(
            webDav =
                WebDavConfig(
                    json.optString("url"),
                    json.optString("username"),
                    json.optString("password"),
                    json.optString("directory", "ChromeXt").ifBlank { "ChromeXt" },
                    json.optString("deviceName", Build.MODEL).ifBlank { Build.MODEL },
                )))
  }

  private fun encodePreferences(preferences: SharedPreferences): JSONObject =
      JSONObject().apply {
        preferences.all.forEach { (key, value) ->
          if (value is Set<*>) put(key, JSONArray(value.filterIsInstance<String>())) else put(key, value)
        }
      }

  private fun restorePreferences(preferences: SharedPreferences, values: JSONObject) {
    val editor = preferences.edit().clear()
    values.keys().forEach { key ->
      when (val value = values.get(key)) {
        is Boolean -> editor.putBoolean(key, value)
        is Int -> editor.putInt(key, value)
        is Long -> editor.putLong(key, value)
        is Double -> editor.putFloat(key, value.toFloat())
        is String -> editor.putString(key, value)
        is JSONArray -> editor.putStringSet(key, (0 until value.length()).map(value::getString).toSet())
      }
    }
    editor.commit()
  }

  private fun copyToTree(source: File, treeUri: Uri, browserPackage: String, keep: Int) {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("本地备份目录不可用")
    val tree = root.requireBrowserDirectory(browserPackage)
    tree.findFile(source.name)?.delete()
    val target =
        tree.createFile("application/zip", source.name) ?: error("无法在所选目录创建备份")
    context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
      source.inputStream().use { it.copyTo(output) }
    } ?: error("无法写入本地备份")
    if (keep > 0) {
      tree.listFiles()
          .filter { it.isFile && it.name?.let(::isBackupName) == true }
          .sortedByDescending(DocumentFile::lastModified)
          .drop(keep)
          .forEach(DocumentFile::delete)
    }
  }

  private fun pruneInternal(browserPackage: String, keep: Int) {
    if (keep == 0) return
    File(backupDirectory, browserDirectoryName(browserPackage))
        .listFiles { file -> file.isFile && isBackupName(file.name) }
        ?.sortedByDescending(File::lastModified)
        ?.drop(keep)
        ?.forEach(File::delete)
  }

  private fun isBackupName(name: String) =
      name.startsWith(WebDavClient.BACKUP_PREFIX) && name.endsWith(".zip", ignoreCase = true)

  private fun isLegacyBackupName(name: String, browserId: String) =
      name.startsWith("${WebDavClient.BACKUP_PREFIX}$browserId-") &&
          name.endsWith(".zip", ignoreCase = true)

  private fun documentBackup(document: DocumentFile): LocalBackup? {
    val name = document.name ?: return null
    if (!document.isFile || !isBackupName(name)) return null
    return LocalBackup(
        name,
        document.lastModified(),
        document.length(),
        documentUri = document.uri,
    )
  }

  private fun File.isJsonDocument(): Boolean =
      inputStream().buffered().use { input ->
        var next = input.read()
        while (next >= 0 && next.toChar().isWhitespace()) next = input.read()
        next == '{'.code || next == '['.code
      }

  companion object {
    const val DEFAULT_LOCAL_PATH = "Documents/ChromeXt"

    fun defaultDocumentsUri(): Uri =
        DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Documents",
        )

    fun displayLocalPath(uri: String): String =
        runCatching {
              DocumentsContract.getTreeDocumentId(Uri.parse(uri))
                  .substringAfter(':')
                  .trim('/')
                  .ifBlank { "Internal storage" }
            }
            .getOrElse {
              Uri.parse(uri).lastPathSegment?.substringAfter(':')?.trim('/').orEmpty().ifBlank {
                uri
              }
            }

    private fun normalizeLocalPath(path: String): String =
        path.replace('\\', '/').trim('/').lowercase()

    private const val PREFS = "backup_settings"
    private const val KEY_URL = "webdav_url"
    private const val KEY_USER = "webdav_user"
    private const val KEY_PASSWORD = "webdav_password"
    private const val KEY_DIRECTORY = "webdav_directory"
    private const val KEY_DEVICE_NAME = "webdav_device_name"
    private const val KEY_LOCAL_RETENTION = "local_retention_count"
    private const val KEY_REMOTE_RETENTION = "remote_retention_count"
    private const val KEY_TREE_URI = "local_tree_uri"
    private const val KEY_ENCRYPTION_PASSWORD = "encryption_password"
    private const val KEY_INCLUDE_WEBDAV_CONFIG = "include_webdav_config"
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val BROWSER_SNAPSHOT_ENTRY = "browser/snapshot.json"
    private const val UI_SETTINGS_ENTRY = "module/ui.json"
    private const val RUNTIME_SETTINGS_ENTRY = "module/runtime.json"
    private const val WEBDAV_CONFIG_ENTRY = "backup/webdav.json"
    private val FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    val RETENTION_VALUES = listOf(1, 3, 5, 10, 0)
  }
}
