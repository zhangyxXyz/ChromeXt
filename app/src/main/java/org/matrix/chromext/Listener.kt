package org.matrix.chromext

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.webkit.WebView
import java.io.File
import java.io.FileReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.devtools.DevSessions
import org.matrix.chromext.devtools.DevToolClient
import org.matrix.chromext.devtools.getInspectPages
import org.matrix.chromext.devtools.hitDevTools
import org.matrix.chromext.bridge.BrowserScriptApi
import org.matrix.chromext.bridge.BrowserBridgeClient
import org.matrix.chromext.extension.LocalFiles
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.Local
import org.matrix.chromext.script.ScriptDbHelper
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.script.parseScript
import org.matrix.chromext.utils.ERUD_URL
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.XMLHttpRequest
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.invalidUserScriptUrls
import org.matrix.chromext.utils.invokeMethod
import org.matrix.chromext.utils.isChromeXtFrontEnd
import org.matrix.chromext.utils.isDevToolsFrontEnd
import org.matrix.chromext.utils.isUserScript
import org.matrix.chromext.utils.matching
import org.matrix.chromext.utils.parseOrigin

object Listener {

  val xmlhttpRequests = mutableMapOf<Double, XMLHttpRequest>()
  val allowedActions =
      mapOf(
          "userscript" to listOf("block", "checkScript", "installScript", "settings"),
          "front-end" to listOf("inspect_pages", "userscript", "extension", "settings"),
          "devtools" to listOf("websocket"),
      )
  val tabNotification = mutableMapOf<Int, Any>()

  private fun syncSharedPreference(
      payload: String,
      item: String,
      cache: MutableMap<String, String>,
  ): String? {
    val result = JSONObject(payload)
    val origin = result.getString("origin")
    val sharedPref = Chrome.getContext().getSharedPreferences(item, Context.MODE_PRIVATE)
    with(sharedPref.edit()) {
      if (result.has("data") && result.optString("data").length > 0) {
        val data = result.getString("data")
        putString(origin, data)
        cache.put(origin, data)
      } else if (cache.containsKey(origin)) {
        remove(origin)
        cache.remove(origin)
      }
      apply()
    }
    return null
  }

  private fun postToPage(event: String, detail: JSONObject, currentTab: Any?, frameId: String?) {
    Handler(Chrome.getContext().mainLooper).post {
      Chrome.evaluateJavascript(listOf("ChromeXt.post('${event}', ${detail});"), currentTab, frameId)
    }
  }

  private fun metaValue(meta: String, key: String): String {
    val pattern = Regex("""(?m)^//\s+@${Regex.escape(key)}\s+(.+)$""")
    return pattern.find(meta)?.groups?.get(1)?.value?.trim() ?: ""
  }

  private fun isScriptSourceUrl(value: String): Boolean {
    return Regex("""^https?://.+\.user\.js(?:[?#].*)?$""", RegexOption.IGNORE_CASE).matches(value)
  }

  private fun installUrlFromMeta(meta: String): String {
    listOf("downloadURL", "installURL", "sourceURL", "url").forEach {
      val value = metaValue(meta, it)
      if (value.startsWith("http://") || value.startsWith("https://")) return value
    }
    val updateUrl = metaValue(meta, "updateURL")
    if (isScriptSourceUrl(updateUrl)) return updateUrl
    val namespace = metaValue(meta, "namespace")
    if (isScriptSourceUrl(namespace)) return namespace
    return ""
  }

  private fun downloadText(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 20000
    connection.instanceFollowRedirects = true
    return connection.inputStream.bufferedReader().use { it.readText() }
  }

  private fun setSourceDisabled(source: String, disabled: Boolean): String {
    val cleaned =
        source
            .replace(Regex("""(?m)^//\s+@(disable|disabled)(\s+.*)?\r?\n?"""), "")
            .replace(Regex("""\n*$"""), "\n")
    if (!disabled) return cleaned
    val end = Regex("""(?m)^// ==/UserScript==\s*$""").find(cleaned) ?: return "// @disable\n" + cleaned
    return cleaned.substring(0, end.range.first) + "// @disable\n" + cleaned.substring(end.range.first)
  }

  private fun transferScripts(
      action: String,
      currentTab: Any?,
      frameId: String?,
  ) {
    val normalized =
        if (action == ScriptTransferContract.ACTION_IMPORT) {
          ScriptTransferContract.ACTION_IMPORT
        } else {
          ScriptTransferContract.ACTION_EXPORT
        }
    Chrome.IO.submit {
      runCatching {
            BrowserBridgeClient.requestTransfer(normalized)
            postToPage(
                "script_transfer_started",
                JSONObject(mapOf("action" to normalized)),
                currentTab,
                frameId,
            )
          }
          .onFailure {
            postToPage(
                "script_error",
                JSONObject(mapOf("message" to (it.message ?: "脚本数据操作失败"))),
                currentTab,
                frameId,
            )
          }
    }
  }

  private fun checkPermisson(action: String, key: Double, tab: Any?): Boolean {
    if (key != Local.key) return false
    val url = Chrome.getUrl(tab)!!
    if (url.endsWith(".txt")) return false
    if (isUserScript(url) && !allowedActions.get("userscript")!!.contains(action)) return false
    if (allowedActions.get("front-end")!!.contains(action) && !isChromeXtFrontEnd(url)) return false
    if (allowedActions.get("devtools")!!.contains(action) && !isDevToolsFrontEnd(url)) return false
    return true
  }

  private fun checkErudaVerison(ctx: Context, callback: (String?) -> Unit) {
    val url = URL(ERUD_URL + "@latest/eruda.js")
    val connection = url.openConnection() as HttpURLConnection
    runCatching {
          connection.inputStream.bufferedReader().use {
            var firstLine = it.readLine()
            val new_version = Local.getErudaVersion(ctx, firstLine)
            if (new_version == null) {
              callback(null)
            } else if (new_version != Local.eruda_version) {
              Local.eruda_version = new_version
              callback(firstLine + "\n" + it.readText())
            } else {
              callback("latest")
            }
            it.close()
          }
        }
        .onFailure { callback(null) }
  }

  fun startAction(text: String, currentTab: Any? = null, auxObject: Any? = null, sourceId: String) {
    var frameId: String? = null
    val sourceParts = sourceId.split("/init/")
    if (sourceParts.size > 1) frameId = sourceParts.last()
    runCatching {
          val data = JSONObject(text)
          val action = data.getString("action")
          val key = data.getDouble("key")
          val payload = data.optString("payload")
          if (checkPermisson(action, key, currentTab)) {
            val callback = on(action, payload, currentTab, auxObject, frameId)
            if (callback != null) Chrome.evaluateJavascript(listOf(callback), currentTab, frameId)
          }
        }
        .onFailure { Log.i("${it::class.java.name}: startAction fails with " + text) }
  }

  fun on(
      action: String,
      payload: String = "",
      currentTab: Any? = null,
      auxObject: Any? = null,
      frameId: String? = null
  ): String? {
    var callback: String? = null
    when (action) {
      "copy" -> {
        val data = JSONObject(payload)
        val type = data.getString("type")
        val text = data.getString("text")
        val label = data.optString("label")
        val clipData =
            when (type) {
              "html" -> ClipData.newHtmlText(label, text, data.optString("htmlText"))
              else -> ClipData.newPlainText(label, text)
            }
        val context = Chrome.getContext()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(clipData)
        val toast = data.optString("toast")
        if (toast.isNotEmpty()) Log.toast(context, toast)
      }
      "toast" -> {
        val data = JSONObject(payload)
        val message = data.optString("message")
        if (message.isNotEmpty()) Log.toast(Chrome.getContext(), message)
      }
      "block" -> {
        val url = Chrome.getUrl(currentTab)
        if (isUserScript(url)) invalidUserScriptUrls.add(url!!)
        callback = "if (Symbol.ChromeXt) Symbol.ChromeXt.lock(${Local.key},'${Local.name}');"
      }
      "close" -> {
        val activity = Chrome.getContext()
        if (WebViewHook.isInit && currentTab != null && auxObject != null) {
          auxObject.invokeMethod(currentTab) { name == "onCloseWindow" }
        } else if (Chrome.isSamsung &&
            currentTab != null &&
            activity::class.java ==
                Chrome.load("com.sec.android.app.sbrowser.SBrowserMainActivity")) {
          val manager = activity.invokeMethod { name == "getTabManager" }!!
          @Suppress("UNCHECKED_CAST")
          val tabList = manager.invokeMethod { name == "getAllTabList" } as List<Any>
          tabList
              .find { it.invokeMethod { name == "getTab" } == currentTab }
              ?.also { manager.invokeMethod(it) { name == "closeTab" } }
        } else if (currentTab != null &&
            activity::class.java == UserScriptProxy.chromeTabbedActivity) {
          val tab = Chrome.load("org.chromium.chrome.browser.tab.Tab")
          val tabModel = Chrome.load("org.chromium.chrome.browser.tabmodel.TabModel")
          val getCurrentTabModel =
              findMethod(activity::class.java, true) {
                parameterTypes.size == 0 && returnType == tabModel
              }
          val model = getCurrentTabModel.invoke(activity)!!
          val closeTab =
              findMethod(model::class.java) {
                returnType == Boolean::class.java &&
                    parameterTypes contentDeepEquals
                        arrayOf(
                            tab,
                            tab,
                            Boolean::class.java,
                            Boolean::class.java,
                            Boolean::class.java,
                            Int::class.java)
              }
          closeTab.invoke(model, currentTab, null, false, false, false, 0)
        } else {
          val msg = "Closing tab ${currentTab} with context ${activity}"
          callback = "console.error(new TypeError('ChromeXt Action failure', {cause: '${msg}'}));"
        }
      }
      "focus" -> {
        Chrome.updateTab(currentTab)
        val detail = JSONObject(payload)
        val requestFocus = detail.getBoolean("requestFocus")
        val activity = Chrome.getContext()
        if (requestFocus &&
            currentTab != null &&
            activity::class.java == UserScriptProxy.chromeTabbedActivity) {
          (activity as Activity).window.decorView.requestFocus()
        }
      }
      "installScript" -> {
        val script = parseScript(payload)
        if (script == null) {
          val detail =
              JSONObject(
                  mapOf(
                      "ok" to false,
                      "message" to "Invalid UserScript: missing or invalid metadata"))
          Handler(Chrome.getContext().mainLooper).post {
            Log.toast(Chrome.getContext(), "ChromeXt: failed to install script")
          }
          callback =
              "if(globalThis.__chromextInstallResult)globalThis.__chromextInstallResult(${detail});try{(Symbol.${Local.name}&&Symbol.${Local.name}.unlock?Symbol.${Local.name}.unlock(${Local.key}):Symbol.ChromeXt).post('install_result', ${detail});}catch(e){}"
        } else {
          val reinstall = ScriptDbManager.scripts.any { it.id == script.id }
          Log.i("Install script ${script.id}")
          ScriptDbManager.apply {
            insert(script)
            scripts.removeAll(scripts.filter { it.id == script.id })
            scripts.add(script)
          }
          val detail =
              JSONObject(
                  mapOf(
                      "ok" to true,
                      "id" to script.id,
                      "reinstall" to reinstall,
                      "message" to if (reinstall) "Script reinstalled" else "Script installed"))
          Handler(Chrome.getContext().mainLooper).post {
            Log.toast(
                Chrome.getContext(),
                if (reinstall) "ChromeXt: script reinstalled" else "ChromeXt: script installed")
          }
          callback =
              "if(globalThis.__chromextInstallResult)globalThis.__chromextInstallResult(${detail});try{(Symbol.${Local.name}&&Symbol.${Local.name}.unlock?Symbol.${Local.name}.unlock(${Local.key}):Symbol.ChromeXt).post('install_result', ${detail});}catch(e){}"
        }
      }
      "checkScript" -> {
        val detail = JSONObject(payload)
        val id = detail.getString("id")
        detail.put("installed", ScriptDbManager.scripts.any { it.id == id })
        callback =
            "if(globalThis.__chromextInstallStatus)globalThis.__chromextInstallStatus(${detail});try{(Symbol.${Local.name}&&Symbol.${Local.name}.unlock?Symbol.${Local.name}.unlock(${Local.key}):Symbol.ChromeXt).post('install_status', ${detail});}catch(e){}"
      }
      "notification" -> {
        val detail = JSONObject(payload)
        val id = detail.getString("id")
        val uuid = detail.getInt("uuid")
        val title = detail.getString("title")
        val text = detail.getString("text")
        val timeout = detail.getLong("timeout")
        val ctx = Chrome.getContext()
        var channel = "xposed_notification"
        if (detail.optBoolean("silent")) channel += "_slient"
        val builder =
            Notification.Builder(ctx, channel)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(
                    Icon.createWithResource("org.matrix.chromext", R.drawable.ic_extension))
                .setTimeoutAfter(timeout)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setLocalOnly(true)
                .setOnlyAlertOnce(true)
        if (detail.optBoolean("onclick")) {
          builder.setContentIntent(ScriptNotification.newIntent(id, uuid, frameId))
          tabNotification.put(uuid, currentTab!!)
        }
        val notificationManager =
            ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (detail.has("image")) {
          Chrome.IO.submit {
            runCatching {
                  val url = URL(detail.getString("image"))
                  val connection = url.openConnection() as HttpURLConnection
                  val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                  builder.setLargeIcon(Icon.createWithBitmap(bitmap))
                }
                .onFailure { Log.d("Fail to set notification image: ${it.message}") }
            notificationManager.notify(id, uuid, builder.build())
          }
        } else {
          notificationManager.notify(id, uuid, builder.build())
        }
        if (detail.optBoolean("highlight")) (ctx as Activity).getWindow().decorView.requestFocus()
      }
      "scriptStorage" -> {
        val detail = JSONObject(payload)
        val id = detail.getString("id")
        val script = ScriptDbManager.scripts.find { it.id == id }
        if (script?.storage == null) return callback
        if (detail.optBoolean("broadcast")) {
          detail.remove("broadcast")
          Chrome.broadcast("scriptStorage", detail) {
            it != null && matching(script, it) && parseOrigin(it) != null
          }
        }
        val data = detail.getJSONObject("data")
        val key = data.getString("key")
        if (data.has("value")) {
          script.storage!!.put(key, data.get("value"))
        } else if (data.has("id")) {
          if (script.storage!!.has(key)) {
            data.put("value", script.storage!!.get(key))
          }
          detail.put("data", data)
          callback = "Symbol.${Local.name}.unlock(${Local.key}).post('scriptSyncValue', ${detail});"
        } else {
          script.storage!!.remove(key)
        }
      }
      "xmlhttpRequest" -> {
        val detail = JSONObject(payload)
        val uuid = detail.getDouble("uuid")
        if (detail.optBoolean("abort")) {
          xmlhttpRequests.get(uuid)?.abort()
        } else {
          val request =
              XMLHttpRequest(
                  detail.getString("id"),
                  detail.getJSONObject("request"),
                  uuid,
                  currentTab,
                  frameId)
          xmlhttpRequests.put(uuid, request)
          Chrome.IO.submit { request.send() }
        }
      }
      "cookie" -> {
        if (WebViewHook.isInit) WebView.setWebContentsDebuggingEnabled(true)
        val detail = JSONObject(payload)
        val method = detail.getString("method")
        val params = detail.optJSONObject("params")
        val data = JSONArray()
        fun checkResult(result: JSONObject): Boolean {
          data.put(result)
          if (result.has("id") && result.getInt("id") == 2) {
            detail.put("response", data)
            detail.remove("params")
            val code = "Symbol.${Local.name}.unlock(${Local.key}).post('cookie', ${detail});"
            Handler(Chrome.getContext().mainLooper).post {
              Chrome.evaluateJavascript(listOf(code), currentTab, frameId)
            }
            return false
          }
          return true
        }
        val url = Chrome.getUrl(currentTab)
        Chrome.IO.submit {
          val tabId = Chrome.getTabId(currentTab, url)
          val client = DevToolClient(tabId, "cookie")
          Chrome.IO.submit { client.listen { if (!checkResult(it)) client.close() } }
          client.command(null, "Network.enable", JSONObject())
          client.command(null, method, params)
        }
      }
      "userAgentSpoof" -> {
        if (UserScriptHook.isInit) {
          val loadUrlParams = UserScriptProxy.newLoadUrlParams(payload)
          if (UserScriptProxy.userAgentHook(payload, loadUrlParams)) {
            UserScriptProxy.loadUrl.invoke(Chrome.getTab(), loadUrlParams)
            callback = "console.log('User-Agent spoofed');"
          }
        }
      }
      "runtimeLauncher" -> {
        val local = Chrome.getContext().getSharedPreferences("ChromeXt", Context.MODE_PRIVATE)
        val data = if (payload.isBlank()) JSONObject() else JSONObject(payload)
        if (!data.optBoolean("read")) {
          val side = if (data.optString("side") == "right") "right" else "left"
          val top = data.optDouble("top", 58.0).coerceAtLeast(0.0)
          local
              .edit()
              .putString("runtime_launcher_side", side)
              .putFloat("runtime_launcher_top", top.toFloat())
              .apply()
        }
        val detail =
            JSONObject(
                mapOf(
                    "side" to local.getString("runtime_launcher_side", "left"),
                    "top" to local.getFloat("runtime_launcher_top", 58f).toDouble(),
                    "enabled" to Chrome.settings.getBoolean("runtime_launcher_enabled", true),
                    "language" to Chrome.settings.getString("language", "system"),
                    "appearance" to BrowserAppearance.payload(Chrome.getContext(), Chrome.settings),
                    "managerUrl" to LocalServer.managerUrl(Chrome.getContext(), "runtime")))
        callback =
            "Symbol.${Local.name}.unlock(${Local.key}).post('runtimeLauncherPosition', ${detail});"
      }
      "settings" -> {
        val detail =
            JSONObject(
                mapOf(
                    "runtimeLauncherEnabled" to
                        Chrome.settings.getBoolean("runtime_launcher_enabled", true),
                    "language" to Chrome.settings.getString("language", "system"),
                    "appearance" to BrowserAppearance.payload(Chrome.getContext(), Chrome.settings)))
        callback = "Symbol.${Local.name}.unlock(${Local.key}).post('settings', ${detail});"
      }
      "erudaSettings" -> {
        val local = Chrome.getContext().getSharedPreferences("ChromeXt", Context.MODE_PRIVATE)
        val data = if (payload.isBlank()) JSONObject() else JSONObject(payload)
        if (!data.optBoolean("read")) {
          val editor = local.edit()
          if (data.has("themeMode")) editor.putString("eruda_theme_mode", data.optString("themeMode", "System"))
          if (data.has("lightTheme")) editor.putString("eruda_light_theme", data.optString("lightTheme", "Light"))
          if (data.has("darkTheme")) editor.putString("eruda_dark_theme", data.optString("darkTheme", "Dark"))
          if (data.has("sourceFormat")) editor.putBoolean("eruda_source_format", data.optBoolean("sourceFormat", true))
          if (data.has("sourceHighlight")) editor.putBoolean("eruda_source_highlight", data.optBoolean("sourceHighlight", true))
          if (data.has("sourceLineNumbers")) editor.putBoolean("eruda_source_line_numbers", data.optBoolean("sourceLineNumbers", true))
          editor.apply()
        }
        val detail =
            JSONObject(
                mapOf(
                    "themeMode" to local.getString("eruda_theme_mode", "System"),
                    "lightTheme" to local.getString("eruda_light_theme", "Light"),
                    "darkTheme" to local.getString("eruda_dark_theme", "Dark"),
                    "sourceFormat" to local.getBoolean("eruda_source_format", true),
                    "sourceHighlight" to local.getBoolean("eruda_source_highlight", true)))
        detail.put("sourceLineNumbers", local.getBoolean("eruda_source_line_numbers", true))
        callback = "Symbol.${Local.name}.unlock(${Local.key}).post('erudaSettings', ${detail});"
      }
      "excludeScript" -> {
        val data = JSONObject(payload)
        val scriptId = data.getString("id")
        val currentUrl = Chrome.getUrl(currentTab)
        val script = ScriptDbManager.scripts.find { it.id == scriptId }
        val host = runCatching { URL(currentUrl ?: "").host }.getOrNull()
        if (script == null || host.isNullOrBlank()) {
          callback =
              "Symbol.${Local.name}.unlock(${Local.key}).post('runtimeActionResult', {ok:false,message:'Failed to exclude current site'});"
        } else {
          val rule = data.optString("rule").takeIf { it.isNotBlank() } ?: "*://${host}/*"
          val label = data.optString("label").takeIf { it.isNotBlank() } ?: host
          if (script.exclude.contains(rule)) {
            callback =
                "Symbol.${Local.name}.unlock(${Local.key}).post('runtimeActionResult', {ok:true,message:'Already excluded ${label}'});"
          } else {
            val marker = "// ==/UserScript=="
            val meta =
                if (script.meta.contains(marker)) {
                  script.meta.replace(marker, "// @exclude ${rule}\n${marker}")
                } else {
                  script.meta + "// @exclude ${rule}\n"
                }
            val newScript = parseScript(meta + script.code, script.storage?.toString())
            if (newScript != null) {
              ScriptDbManager.insert(newScript)
              ScriptDbManager.scripts.remove(script)
              ScriptDbManager.scripts.add(newScript)
              callback =
                  "Symbol.${Local.name}.unlock(${Local.key}).post('runtimeActionResult', {ok:true,message:'Excluded ${label}. Refresh to apply.'});"
            } else {
              callback =
                  "Symbol.${Local.name}.unlock(${Local.key}).post('runtimeActionResult', {ok:false,message:'Failed to update script metadata'});"
            }
          }
        }
      }
      "loadEruda" -> {
        val ctx = Chrome.getContext()
        val eruda = File(ctx.filesDir, "Eruda.js")
        val cachedVersion = Local.getErudaVersion(ctx)
        val official =
            if (cachedVersion != null && eruda.exists()) {
              FileReader(eruda).use { it.readText() }
            } else {
              Local.getBundledEruda(ctx)
            }
        val code =
            "if(globalThis.eruda&&globalThis.__ChromeXtErudaAdapted){" +
                "${Local.erudaStyles}" +
                "globalThis.__ChromeXtRefreshErudaStyles?.();" +
                "globalThis.__ChromeXtShowEruda?.();" +
                "}else if(globalThis.eruda){" +
                "{${Local.eruda}}" +
                "}else{" +
                official +
                "\n{${Local.eruda}}" +
                "}\n//# sourceURL=local://ChromeXt/load-eruda"
        Chrome.evaluateJavascript(listOf(code), currentTab, frameId)
        if (cachedVersion == null) {
          // The bundled copy makes first/offline use immediate. Refresh the cache silently for
          // the next load when the network is available.
          Chrome.IO.submit {
            checkErudaVerison(ctx) { downloaded ->
              if (downloaded != null && downloaded != "latest") {
                File(ctx.filesDir, "Eruda.js").writeText(downloaded)
              }
            }
          }
        }
      }
      "hideEruda" -> {
        Chrome.evaluateJavascript(
            listOf(
                "if(globalThis.eruda){" +
                    "if(globalThis.__ChromeXtHideEruda){globalThis.__ChromeXtHideEruda();}" +
                    "else{eruda.hide();const entry=eruda._entryBtn?._\$el?.[0];" +
                    "eruda._entryBtn?.hide?.();" +
                    "entry?.style?.setProperty('display','none','important');" +
                    "globalThis.__ChromeXtErudaVisible=false;}" +
                    "}"),
            currentTab,
            frameId)
      }
      "updateEruda" -> {
        val ctx = Chrome.getContext()
        Handler(ctx.mainLooper).post { Log.toast(ctx, "Updating Eruda...") }
        Chrome.IO.submit {
          checkErudaVerison(ctx) {
            val msg =
                if (it == "latest") {
                  "Eruda is already the latest"
                } else if (it != null) {
                  "Updated to eruda v" + Local.eruda_version
                } else {
                  "Failed to download Eruda.js from ${ERUD_URL}"
                }
            Handler(ctx.mainLooper).post { Log.toast(ctx, msg) }
            if (it != null) {
              if (it != "latest") {
                val file = File(ctx.filesDir, "Eruda.js")
                file.outputStream().write(it.toByteArray())
              }
              if (payload != "" && JSONObject(payload).optBoolean("load")) on("loadEruda")
            } else if (payload != "" && JSONObject(payload).optBoolean("load")) {
              // Network failed and no usable cache is required: load the bundled asset.
              on("loadEruda")
            }
          }
        }
      }
      "syncData" -> {
        val type = JSONObject(payload).getString("name")
        callback =
            when (type) {
              "filters" ->
                  syncSharedPreference(payload, "CosmeticFilter", ScriptDbManager.cosmeticFilters)
              "userAgent" -> syncSharedPreference(payload, "UserAgent", ScriptDbManager.userAgents)
              "cspRules" -> syncSharedPreference(payload, "CSPRule", ScriptDbManager.cspRules)
              else -> null
            }
      }
      "inspectPages" -> {
        if (WebViewHook.isInit) WebView.setWebContentsDebuggingEnabled(true)
        Chrome.IO.submit {
          val code = "ChromeXt.post('inspect_pages', ${getInspectPages()});"
          Handler(Chrome.getContext().mainLooper).post {
            Chrome.evaluateJavascript(listOf(code), currentTab, frameId)
          }
        }
      }
      "userscript" -> {
        if (payload == "") {
          val detail = JSONObject(mapOf("type" to "init"))
          detail.put("ids", JSONArray(ScriptDbManager.scripts.map { it.id }))
          callback = "ChromeXt.post('userscript', ${detail});"
          } else {
            val data = JSONObject(payload)
          val appTransfer =
              data.optString("appTransfer").ifBlank {
                if (data.optBoolean("export")) ScriptTransferContract.ACTION_EXPORT else ""
              }
          if (data.optBoolean("transferStatus")) {
            val status = JSONObject(BrowserScriptApi.request("takeTransferStatus", ""))
            if (status.optBoolean("pending")) {
              callback = "ChromeXt.post('script_transfer_complete', ${status.getJSONObject("result")});"
            }
          } else if (appTransfer.isNotBlank()) {
            transferScripts(appTransfer, currentTab, frameId)
          } else if (data.optBoolean("browserExport")) {
            val bundle = JSONObject(BrowserScriptApi.request("exportBundle", ""))
            val browserPackage = Chrome.getContext().packageName
            bundle.put("browserPackage", browserPackage)
            val detail =
                JSONObject(
                    mapOf(
                        "count" to (bundle.optJSONArray("scripts")?.length() ?: 0),
                        "name" to
                            "ChromeXt-userscripts-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.json",
                        "content" to bundle.toString(2),
                    ))
            callback = "ChromeXt.post('script_export', $detail);"
          } else if (data.has("import")) {
            val sources = data.getJSONArray("import")
            var imported = 0
            var failed = 0
            for (i in 0 until sources.length()) {
              val script = parseScript(sources.optString(i))
              if (script == null) {
                failed++
                continue
              }
              ScriptDbManager.insert(script)
              ScriptDbManager.scripts.removeAll { it.id == script.id }
              ScriptDbManager.scripts.add(script)
              imported++
            }
            callback =
                "ChromeXt.post('script_imported', ${JSONObject(mapOf("imported" to imported, "failed" to failed))});"
          } else if (data.has("reinstall")) {
            val idsJson = data.getJSONArray("reinstall")
            val ids = Array(idsJson.length()) { idsJson.getString(it) }
            Chrome.IO.submit {
              var updated = 0
              var failed = 0
              ids.forEach { id ->
                val oldScript = ScriptDbManager.scripts.find { it.id == id }
                val installUrl = oldScript?.meta?.let { installUrlFromMeta(it) } ?: ""
                if (oldScript == null || installUrl.isEmpty()) {
                  failed++
                  return@forEach
                }
                runCatching {
                      val source = setSourceDisabled(downloadText(installUrl), oldScript.disabled)
                      val newScript = parseScript(source, oldScript.storage?.toString())
                      if (newScript == null) {
                        failed++
                      } else {
                        ScriptDbManager.insert(newScript)
                        ScriptDbManager.scripts.removeAll { it.id == oldScript.id || it.id == newScript.id }
                        ScriptDbManager.scripts.add(newScript)
                        updated++
                      }
                    }
                    .onFailure { failed++ }
              }
              postToPage(
                  "script_reinstalled",
                  JSONObject(mapOf("updated" to updated, "failed" to failed)),
                  currentTab,
                  frameId)
            }
          } else if (data.has("source")) {
            val script =
                parseScript(
                    data.getString("source"),
                    ScriptDbManager.scripts
                        .find { it.id == data.optString("previousId") }
                        ?.storage
                        ?.toString())
            if (script != null) {
              data.optString("previousId").takeIf { it.length > 0 && it != script.id }?.let { id ->
                val dbHelper = ScriptDbHelper(Chrome.getContext())
                val db = dbHelper.writableDatabase
                db.delete("script", "id = ?", arrayOf(id))
                ScriptDbManager.scripts.removeAll { it.id == id }
                dbHelper.close()
              }
              ScriptDbManager.insert(script)
              ScriptDbManager.scripts.removeAll { it.id == script.id }
              ScriptDbManager.scripts.add(script)
              callback =
                  "ChromeXt.post('script_saved', ${JSONObject(mapOf("id" to script.id))});"
            } else {
              callback = "ChromeXt.post('script_error', {message:'Invalid userscript metadata'});"
            }
          } else if (data.has("read")) {
            val script = ScriptDbManager.scripts.find { it.id == data.getString("read") }
            callback =
                if (script == null) {
                  "ChromeXt.post('script_error', {message:'Script not found'});"
                } else {
                  val detail =
                      JSONObject(
                          mapOf(
                              "id" to script.id,
                              "source" to script.meta + script.code,
                              "meta" to script.meta))
                  "ChromeXt.post('script_detail', ${detail});"
                }
          } else if (data.has("meta")) {
            val script = ScriptDbManager.scripts.filter { it.id == data.getString("id") }.first()
            val newScript =
                parseScript(data.getString("meta") + script.code, script.storage?.toString())
            if (newScript != null) {
              ScriptDbManager.insert(newScript)
              ScriptDbManager.scripts.remove(script)
              ScriptDbManager.scripts.add(newScript)
              callback =
                  "ChromeXt.post('script_meta_saved', ${JSONObject(mapOf("id" to newScript.id))});"
            } else {
              callback = "ChromeXt.post('script_error', {message:'Fail to update script metadata'});"
            }
          } else if (data.has("ids")) {
            val jsonArray = data.getJSONArray("ids")
            val ids = Array(jsonArray.length()) { jsonArray.getString(it) }
            val scripts = ScriptDbManager.scripts.filter { ids.contains(it.id) }
            if (data.optBoolean("delete")) {
              val dbHelper = ScriptDbHelper(Chrome.getContext())
              val db = dbHelper.writableDatabase
              db.delete("script", "id = ?", ids)
              ScriptDbManager.scripts.removeAll(scripts)
              dbHelper.close()
            } else {
              val result = JSONArray(scripts.map { it.meta })
              callback = "ChromeXt.post('script_meta', ${result});"
            }
          }
        }
      }
      "extension" -> {
        if (payload == "") {
          if (BuildConfig.DEBUG) {
            callback = "ChromeXt.post('extension', ${LocalFiles.start()});"
          } else {
            Log.toast(Chrome.getContext(), "Work in progress, might be ready in the future :)")
          }
        }
      }
      "websocket" -> {
        val detail = JSONObject(payload)
        val targetTabId = detail.getString("targetTabId")
        var target = DevSessions.get { it.tabId == targetTabId && it.tag == "transit" }
        if (detail.has("message")) {
          val message = JSONObject(detail.getString("message"))
          target?.command(
              message.getInt("id"), message.getString("method"), message.optJSONObject("params"))
        } else {
          fun response(res: JSONObject) {
            if (Chrome.checkTab(currentTab)) {
              Handler(Chrome.getContext().mainLooper).post {
                Chrome.evaluateJavascript(
                    listOf("ChromeXt.post('websocket', ${res})"), currentTab, frameId)
              }
            } else {
              target?.close()
            }
          }
          Chrome.IO.submit {
            target?.close()
            hitDevTools().close()
            val session = DevToolClient(targetTabId, "transit")
            target = session
            if (!session.isClosed()) {
              DevSessions.add(session)
              response(JSONObject(mapOf("open" to true)))
              session.listen { response(JSONObject(mapOf("message" to it))) }
            }
            response(JSONObject(mapOf("error" to "Remote session closed")))
          }
        }
      }
    }
    return callback
  }
}

private class ScriptNotification(detail: JSONObject, frameId: String?) : BroadcastReceiver() {
  private val detail = detail
  private val frameId = frameId

  companion object {
    const val ACTION_USERSCRIPT = "ChromeXt"
    const val UUID = "GM_notification"

    fun newIntent(id: String, uuid: Int, frameId: String?): PendingIntent {
      val ctx = Chrome.getContext()
      val detail = JSONObject(mapOf("id" to id, "uuid" to uuid))
      ctx.registerReceiver(ScriptNotification(detail, frameId), IntentFilter(ACTION_USERSCRIPT))
      val intent =
          Intent().apply {
            setAction(ACTION_USERSCRIPT)
            putExtra(UUID, uuid)
          }
      return PendingIntent.getBroadcast(ctx, uuid, intent, PendingIntent.FLAG_IMMUTABLE)
    }
  }

  override fun onReceive(ctx: Context, intent: Intent) {
    if (intent.getAction() == ACTION_USERSCRIPT) {
      val uuid = intent.getIntExtra(UUID, 0)
      if (uuid == detail.getInt("uuid")) {
        val tab = Listener.tabNotification.get(detail.getInt("uuid"))!!
        val code = "Symbol.${Local.name}.unlock(${Local.key}).post('notification', ${detail});"
        Chrome.evaluateJavascript(listOf(code), tab, frameId)
        ctx.unregisterReceiver(this)
        Listener.tabNotification.remove(uuid)
      }
    }
  }
}
