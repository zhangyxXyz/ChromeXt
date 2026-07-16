package org.matrix.chromext.backup

import java.io.File
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource

data class WebDavConfig(
    val url: String,
    val username: String,
    val password: String,
    val directory: String = "ChromeXt",
    val deviceName: String = "Android",
) {
  val isConfigured: Boolean
    get() = url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

data class RemoteBackup(val name: String, val modifiedAt: Long, val size: Long)

class WebDavClient(
    private val config: WebDavConfig,
    private val http: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(45))
            .writeTimeout(Duration.ofSeconds(90))
            .build(),
) {
  private val authorization = Credentials.basic(config.username, config.password)
  private val rootUrl = buildRootUrl(config)

  fun test() {
    ensureRoot()
    execute(
            Request.Builder()
                .url(rootUrl)
                .header("Depth", "1")
                .method("PROPFIND", EMPTY_BODY)
                .build())
        .close()
  }

  fun listBackups(): List<RemoteBackup> {
    ensureRoot()
    val body =
        execute(
                Request.Builder()
                    .url(rootUrl)
                    .header("Depth", "1")
                    .method("PROPFIND", PROPFIND_BODY)
                    .build())
            .use { it.body?.string().orEmpty() }
    if (body.isBlank()) return emptyList()
    val factory =
        DocumentBuilderFactory.newInstance().apply {
          isNamespaceAware = true
          isExpandEntityReferences = false
          runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
          runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
          runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
          runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
          runCatching {
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
          }
        }
    val builder =
        factory.newDocumentBuilder().apply {
          setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
    val document = builder.parse(body.byteInputStream())
    return (0 until document.getElementsByTagNameNS("*", "response").length)
        .mapNotNull { index ->
          val response = document.getElementsByTagNameNS("*", "response").item(index) as Element
          val href = response.text("href") ?: return@mapNotNull null
          val name = URLDecoder.decode(URI(href).path.substringAfterLast('/'), Charsets.UTF_8.name())
          if (!name.startsWith(BACKUP_PREFIX) || !name.endsWith(".zip")) return@mapNotNull null
          RemoteBackup(
              name,
              response.text("getlastmodified")?.let {
                runCatching {
                      DateTimeFormatter.RFC_1123_DATE_TIME.parse(it, Instant::from).toEpochMilli()
                    }
                    .getOrNull()
              } ?: 0L,
              response.text("getcontentlength")?.toLongOrNull() ?: 0L,
          )
        }
        .sortedByDescending(RemoteBackup::modifiedAt)
  }

  fun upload(file: File) {
    ensureRoot()
    execute(
            Request.Builder()
                .url(fileUrl(file.name))
                .put(file.asRequestBody("application/zip".toMediaType()))
                .build())
        .close()
  }

  fun download(name: String, target: File): File {
    require(name.matches(Regex("chromext-backup-[\\p{L}\\p{N}._-]+\\.zip"))) {
      "Invalid backup name"
    }
    execute(Request.Builder().url(fileUrl(name)).get().build()).use { response ->
      target.parentFile?.mkdirs()
      response.body?.byteStream()?.use { input -> target.outputStream().use(input::copyTo) }
          ?: error("WebDAV returned an empty response")
    }
    return target
  }

  fun delete(name: String) {
    execute(Request.Builder().url(fileUrl(name)).delete().build()).close()
  }

  fun prune(keepCount: Int) {
    if (keepCount == 0) return
    listBackups().drop(keepCount).forEach { delete(it.name) }
  }

  private fun ensureRoot() {
    val base = config.url.trim().trimEnd('/') + "/"
    var current = base
    config.directory.split('/').map(String::trim).filter(String::isNotEmpty).forEach { segment ->
      current += encode(segment) + "/"
      execute(
              Request.Builder().url(current).method("MKCOL", EMPTY_BODY).build(),
              setOf(201, 405))
          .close()
    }
  }

  private fun execute(request: Request, accepted: Set<Int>? = null): okhttp3.Response {
    val response =
        http.newCall(request.newBuilder().header("Authorization", authorization).build()).execute()
    if (accepted?.contains(response.code) != true && (accepted != null || !response.isSuccessful)) {
      response.close()
      error("WebDAV ${request.method} failed: HTTP ${response.code} ${response.message}".trim())
    }
    return response
  }

  private fun fileUrl(name: String) = rootUrl + encode(name)

  private fun Element.text(localName: String): String? =
      getElementsByTagNameNS("*", localName).item(0)?.textContent?.trim()?.takeIf(String::isNotEmpty)

  companion object {
    const val BACKUP_PREFIX = "chromext-backup-"
    private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    private val PROPFIND_BODY =
        """<?xml version="1.0" encoding="utf-8" ?><d:propfind xmlns:d="DAV:"><d:prop><d:getlastmodified/><d:getcontentlength/><d:resourcetype/></d:prop></d:propfind>"""
            .toRequestBody("application/xml; charset=utf-8".toMediaType())

    private fun buildRootUrl(config: WebDavConfig): String {
      var result = config.url.trim().trimEnd('/') + "/"
      config.directory.split('/').map(String::trim).filter(String::isNotEmpty).forEach {
        result += encode(it) + "/"
      }
      return result
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
  }
}
