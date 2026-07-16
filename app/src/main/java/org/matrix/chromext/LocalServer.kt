package org.matrix.chromext

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLConnection
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import org.json.JSONObject
import org.matrix.chromext.script.Local
import org.matrix.chromext.utils.Log

object LocalServer {
  const val PREF_LOCAL_SERVER_ENABLED = "local_server_enabled"

  private val executor = Executors.newFixedThreadPool(8)
  private var socket: ServerSocket? = null
  private var port: Int = 0

  private val localHosts = setOf("chrome.local", "chromext.local")

  @Synchronized
  fun ensureStarted(ctx: Context = Chrome.getContext()): Int {
    socket
        ?.takeIf { !it.isClosed }
        ?.let {
          return port
        }
    val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    socket = server
    port = server.localPort
    executor.execute {
      while (!server.isClosed) {
        runCatching {
              val client = server.accept()
              executor.execute {
                client.use {
                  it.soTimeout = 5_000
                  runCatching {
                        handle(
                            ctx.applicationContext ?: ctx,
                            it.getInputStream().bufferedReader(),
                            it.getOutputStream(),
                        )
                      }
                      .onFailure { error -> Log.ex(error) }
                }
              }
            }
            .onFailure { if (!server.isClosed) Log.ex(it) }
      }
    }
    Log.i("Local server started on 127.0.0.1:${port}")
    return port
  }

  @Synchronized
  fun stop() {
    val server = socket ?: return
    socket = null
    port = 0
    runCatching { server.close() }
        .onSuccess { Log.i("Local server stopped") }
        .onFailure { Log.ex(it) }
  }

  fun rewrite(url: String?): String? {
    if (!isLocalDomainUrl(url)) return null
    val ctx = Chrome.getContext()
    if (!Chrome.settings.getBoolean(PREF_LOCAL_SERVER_ENABLED, false)) {
      return null
    }
    val localPort = ensureStarted(ctx)
    val uri = Uri.parse(url)
    val path = uri.encodedPath?.takeIf { it.isNotEmpty() } ?: "/"
    val query = uri.encodedQuery?.let { "?${it}" } ?: ""
    val fragment = uri.encodedFragment?.let { "#${it}" } ?: ""
    return "http://127.0.0.1:${localPort}${path}${query}${fragment}"
  }

  fun managerUrl(ctx: Context = Chrome.getContext(), source: String): String {
    if (!Chrome.settings.getBoolean(PREF_LOCAL_SERVER_ENABLED, false)) {
      return "https://chromext.local/?from=${source}"
    }
    return "http://127.0.0.1:${ensureStarted(ctx)}/?from=${source}"
  }

  fun isFrontEndUrl(url: String?): Boolean {
    if (isLocalDomainUrl(url)) return true
    val localPort =
        synchronized(this) {
          socket?.takeIf { !it.isClosed } ?: return false
          port.takeIf { it > 0 } ?: return false
        }
    return runCatching {
          val uri = Uri.parse(url)
          uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port == localPort
        }
        .getOrDefault(false)
  }

  fun isLocalDomainUrl(url: String?): Boolean {
    if (url == null) return false
    return runCatching {
          val uri = Uri.parse(url)
          (uri.scheme == "https" || uri.scheme == "http") && localHosts.contains(uri.host)
        }
        .getOrDefault(false)
  }

  private fun handle(ctx: Context, reader: BufferedReader, output: OutputStream) {
    val startedAt = System.currentTimeMillis()
    val requestLine = reader.readLine() ?: return
    if (requestLine.length > 4_096) {
      respond(output, 414, "text/plain", "URI Too Long".toByteArray())
      return
    }
    val parts = requestLine.split(" ")
    if (parts.size < 2) return
    val method = parts[0]
    var headerBytes = 0
    var headerCount = 0
    while (true) {
      val header = reader.readLine() ?: return
      if (header.isEmpty()) break
      headerBytes += header.length
      headerCount += 1
      if (headerBytes > 8_192 || headerCount > 64) {
        respond(output, 431, "text/plain", "Request Header Fields Too Large".toByteArray())
        return
      }
    }
    if (method != "GET" && method != "HEAD") {
      respond(output, 405, "text/plain", "Method Not Allowed".toByteArray())
      return
    }
    val path =
        URLDecoder.decode(
                parts[1].substringBefore("?").substringBefore("#"),
                StandardCharsets.UTF_8.name(),
            )
            .trimStart('/')
            .ifEmpty { "index.html" }
    if (path.contains("..")) {
      respond(output, 403, "text/plain", "Forbidden".toByteArray())
      return
    }
    val assetPath = if (path == "index.html") "frontend/index.html" else "frontend/${path}"
    val bytes =
        runCatching {
              Resource.enrich(ctx)
              if (assetPath == "frontend/index.html") localFrontEndHtml(ctx).toByteArray()
              else ctx.assets.open(assetPath).use { it.readBytes() }
            }
            .getOrElse {
              respond(output, 404, "text/plain", "Not Found".toByteArray())
              return
            }
    val body = if (method == "HEAD") ByteArray(0) else bytes
    respond(
        output,
        200,
        URLConnection.guessContentTypeFromName(assetPath) ?: "text/plain",
        body,
        bytes.size,
    )
    if (BuildConfig.DEBUG) {
      Log.d("Local server ${method} /${path} -> ${bytes.size} bytes in ${System.currentTimeMillis() - startedAt}ms")
    }
  }

  private fun localFrontEndHtml(ctx: Context): String {
    val language = JSONObject.quote(Chrome.settings.getString("language", "system") ?: "system")
    val appearance = BrowserAppearance.payload(ctx, Chrome.settings)
    val zh =
        JSONObject(ctx.assets.open("frontend/i18n/zh.json").bufferedReader().use { it.readText() })
    val i18n =
        JSONObject(
            mapOf(
                "en" to JSONObject(ctx.assets.open("frontend/i18n/en.json").bufferedReader().use { it.readText() }),
                "zh" to zh,
                "zh-TW" to UiLocalization.traditionalJson(zh)))
    Resource.enrich(ctx)
    val init =
        Local.initChromeXt +
            "\nglobalThis.ChromeXt = Symbol.ChromeXt;\n" +
            "globalThis.__ChromeXtLanguage = ${language};\n" +
            "globalThis.__ChromeXtAppearance = ${appearance};\n" +
            "globalThis.__ChromeXtI18n = ${i18n};\n" +
            "//# sourceURL=local://ChromeXt/init"
    return ctx.assets
        .open("frontend/index.html")
        .bufferedReader()
        .use { it.readText() }
        .replace("<head>", "<head><script>${init}</script>")
  }

  private fun respond(
      output: OutputStream,
      status: Int,
      mimeType: String,
      body: ByteArray,
      contentLength: Int = body.size,
  ) {
    val reason =
        when (status) {
          200 -> "OK"
          403 -> "Forbidden"
          404 -> "Not Found"
          405 -> "Method Not Allowed"
          else -> "Error"
        }
    val header =
        "HTTP/1.1 ${status} ${reason}\r\n" +
            "Content-Type: ${mimeType}; charset=UTF-8\r\n" +
            "Content-Length: ${contentLength}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Connection: close\r\n\r\n"
    output.write(header.toByteArray())
    output.write(body)
    output.flush()
  }
}
