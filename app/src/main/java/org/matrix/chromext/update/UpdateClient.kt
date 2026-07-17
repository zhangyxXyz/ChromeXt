package org.matrix.chromext.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class ReleaseAsset(val name: String, val url: String, val size: Long)

data class AppRelease(
    val tag: String,
    val name: String,
    val body: String,
    val pageUrl: String,
    val publishedAt: String,
    val apk: ReleaseAsset?,
    val prerelease: Boolean = false,
)

class UpdateClient(
    private val http: OkHttpClient = OkHttpClient(),
    private val repository: String = "zhangyxXyz/ChromeXt",
) {
  suspend fun latestRelease(): Result<AppRelease?> =
      withContext(Dispatchers.IO) {
        cancellableResult {
          val request =
              Request.Builder()
                  .url("https://api.github.com/repos/$repository/releases/latest")
                  .header("Accept", "application/vnd.github+json")
                  .header("X-GitHub-Api-Version", "2022-11-28")
                  .build()
          http.newCall(request).execute().use { response ->
            if (response.code == 404) return@use null
            if (!response.isSuccessful) error("GitHub HTTP ${response.code} ${response.message}")
            parseRelease(JSONObject(response.body?.string().orEmpty()))
          }
        }
      }

  suspend fun releases(): Result<List<AppRelease>> =
      withContext(Dispatchers.IO) {
        cancellableResult {
          val request =
              Request.Builder()
                  .url("https://api.github.com/repos/$repository/releases?per_page=30")
                  .header("Accept", "application/vnd.github+json")
                  .header("X-GitHub-Api-Version", "2022-11-28")
                  .build()
          http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub HTTP ${response.code} ${response.message}")
            val json = org.json.JSONArray(response.body?.string().orEmpty())
            (0 until json.length()).map { parseRelease(json.getJSONObject(it)) }
          }
        }
      }

  suspend fun readme(languageTag: String): Result<String> =
      withContext(Dispatchers.IO) {
        cancellableResult {
          val normalized = languageTag.trim().replace('_', '-').trim('-')
          val language = normalized.substringBefore('-').lowercase()
          val candidates =
              buildList {
                    if (normalized.isNotBlank() && language != "en") add("README.$normalized.md")
                    if (language.isNotBlank() && language != "en" && !normalized.equals(language, true)) {
                      add("README.$language.md")
                    }
                    add("README.md")
                  }
                  .distinct()
          for (fileName in candidates) {
            val request =
                Request.Builder()
                    .url("https://api.github.com/repos/$repository/contents/$fileName")
                    .header("Accept", "application/vnd.github.raw+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .build()
            http.newCall(request).execute().use { response ->
              if (response.isSuccessful) return@cancellableResult response.body?.string().orEmpty()
              if (response.code != 404) {
                error("GitHub HTTP ${response.code} ${response.message}")
              }
            }
          }
          error("README not found")
        }
      }

  private fun parseRelease(json: JSONObject): AppRelease {
    val assets = json.optJSONArray("assets")
    var chosen: ReleaseAsset? = null
    if (assets != null) {
      for (index in 0 until assets.length()) {
        val asset = assets.getJSONObject(index)
        val name = asset.optString("name")
        if (name.endsWith(".apk", ignoreCase = true) &&
            name.contains("ChromeXt", ignoreCase = true) &&
            !name.contains("debug", ignoreCase = true)) {
          chosen =
              ReleaseAsset(
                  name,
                  asset.optString("browser_download_url"),
                  asset.optLong("size"),
              )
          break
        }
      }
    }
    return AppRelease(
        json.optString("tag_name"),
        json.optString("name").ifBlank { json.optString("tag_name") },
        json.optString("body"),
        json.optString("html_url"),
        json.optString("published_at"),
        chosen,
        json.optBoolean("prerelease"),
    )
  }

  suspend fun download(
      asset: ReleaseAsset,
      target: File,
      progress: suspend (Long, Long) -> Unit,
  ): Result<File> =
      withContext(Dispatchers.IO) {
        cancellableResult {
              target.parentFile?.mkdirs()
              val temporary = File(target.parentFile, "${target.name}.part").apply { delete() }
              http.newCall(Request.Builder().url(asset.url).build()).execute().use { response ->
                if (!response.isSuccessful) error("GitHub HTTP ${response.code} ${response.message}")
                val body = response.body ?: error("Empty update response")
                val total = body.contentLength()
                progress(0L, total)
                temporary.outputStream().buffered().use { output ->
                  body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    var reported = 0L
                    while (true) {
                      val count = input.read(buffer)
                      if (count < 0) break
                      output.write(buffer, 0, count)
                      copied += count
                      if (copied - reported >= 256L * 1024L || copied == total) {
                        progress(copied, total)
                        reported = copied
                      }
                    }
                    if (reported != copied) progress(copied, total)
                  }
                }
              }
              require(isApkArchive(temporary)) { "Downloaded file is not a valid APK" }
              target.delete()
              check(temporary.renameTo(target)) { "Could not save downloaded APK" }
              target
            }
            .onFailure { File(target.parentFile, "${target.name}.part").delete() }
      }

  companion object {
    const val PROJECT_URL = "https://github.com/zhangyxXyz/ChromeXt"

    fun validateAndInstall(context: Context, file: File) {
      val packageManager = context.packageManager
      val archive =
          packageManager.getPackageArchiveInfo(
              file.absolutePath,
              if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
              else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES)
              ?: error("无法读取更新 APK")
      require(archive.packageName == context.packageName) { "更新 APK 包名不匹配" }
      val current =
          packageManager.getPackageInfo(
              context.packageName,
              if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
              else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES)
      require(signatures(archive).isNotEmpty() && signatures(archive) == signatures(current)) {
        "更新 APK 签名不匹配"
      }
      val uri =
          FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      context.startActivity(
          Intent(Intent.ACTION_VIEW)
              .setDataAndType(uri, "application/vnd.android.package-archive")
              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun signatures(info: android.content.pm.PackageInfo): Set<String> =
        if (Build.VERSION.SDK_INT >= 28) {
          info.signingInfo
              ?.apkContentsSigners
              ?.map { it.toByteArray().joinToString("") { byte -> "%02x".format(byte) } }
              ?.toSet()
              .orEmpty()
        } else {
          @Suppress("DEPRECATION")
          info.signatures
              ?.map { it.toByteArray().joinToString("") { byte -> "%02x".format(byte) } }
              ?.toSet()
              .orEmpty()
        }
  }
}

fun isNewerVersion(tag: String, current: String): Boolean =
    compareVersions(tag, current) > 0

fun isSameVersion(tag: String, current: String): Boolean =
    compareVersions(tag, current) == 0

private suspend fun <T> cancellableResult(block: suspend () -> T): Result<T> =
    try {
      Result.success(block())
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (failure: Throwable) {
      Result.failure(failure)
    }

private fun compareVersions(left: String, right: String): Int {
  fun parts(value: String): List<Int> =
      value
          .trim()
          .removePrefix("v")
          .removePrefix(".")
          .substringBefore('-')
          .split('.')
          .map { it.toIntOrNull() ?: 0 }
          .dropLastWhile { it == 0 }
  val a = parts(left)
  val b = parts(right)
  val size = maxOf(a.size, b.size)
  for (index in 0 until size) {
    val result = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
    if (result != 0) return result
  }
  return 0
}

private fun isApkArchive(file: File): Boolean =
    runCatching {
          ZipFile(file).use {
            it.getEntry("AndroidManifest.xml") != null && it.getEntry("classes.dex") != null
          }
        }
        .getOrDefault(false)
