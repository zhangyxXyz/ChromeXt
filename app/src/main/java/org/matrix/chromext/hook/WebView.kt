package org.matrix.chromext.hook

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.lang.ref.WeakReference
import java.net.URLConnection
import java.util.WeakHashMap
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.Resource
import org.matrix.chromext.script.Local
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findField
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.findMethodOrNull
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore
import org.matrix.chromext.utils.hookMethod
import org.matrix.chromext.utils.invokeMethod
import org.matrix.chromext.utils.isChromeXtFrontEnd
import org.matrix.chromext.utils.isLocalChromeXtResource

object WebViewHook : BaseHook() {

  var ViewClient: Class<*>? = null
  var ChromeClient: Class<*>? = null
  var WebView: Class<*>? = null
  val extraWebViews = mutableSetOf<Class<*>>()
  val records = mutableListOf<WeakReference<Any>>()
  private val hookedViewClientMethods = mutableSetOf<Method>()
  private val injectedUrls = WeakHashMap<Any, MutableSet<String>>()
  private val injectedAt = WeakHashMap<Any, MutableMap<String, Long>>()
  private val recentlyInjectedUrls = mutableMapOf<String, Long>()
  private val loadedLocalFrontEnds = WeakHashMap<Any, String>()
  private const val reinjectionProbeCooldownMs = 1500L

  private fun webViewClasses(): List<Class<*>> = (listOfNotNull(WebView) + extraWebViews).distinct()

  fun isWebView(view: Any?): Boolean {
    return view != null && webViewClasses().any { it.isAssignableFrom(view::class.java) }
  }

  private fun discoverWebViews(root: View?): List<Any> {
    if (root == null) return emptyList()
    val result = mutableListOf<Any>()
    fun visit(view: View) {
      if (isWebView(view)) {
        result.add(view)
        return
      }
      if (view is ViewGroup) {
        for (index in 0 until view.childCount) visit(view.getChildAt(index))
      }
    }
    visit(root)
    return result
  }

  fun evaluateJavascript(code: String?, view: Any?) {
    val webView = (view ?: Chrome.getTab())
    if (code != null && code.length > 0 && webView != null) {
      val evaluate = {
        runCatching {
              val webSettings = webView.invokeMethod { name == "getSettings" }
              if (webSettings?.invokeMethod { name == "getJavaScriptEnabled" } == true) {
                webView.invokeMethod(code, null) {
                  name == "evaluateJavascript" && parameterCount == 2
                }
              }
            }
            .recoverCatching {
              webView.invokeMethod(code) { name == "evaluateJavascript" && parameterCount == 1 }
            }
            .onFailure { Log.ex(it) }
      }
      if (Looper.myLooper() == Chrome.getContext().mainLooper) {
        evaluate()
      } else {
        Handler(Chrome.getContext().mainLooper).post { evaluate() }
      }
    }
  }

  private fun evaluateJavascriptForResult(
      webView: Any,
      code: String,
      callback: (String?) -> Unit
  ): Boolean {
    val method =
        findMethodOrNull(webView::class.java, true) {
          name == "evaluateJavascript" &&
              parameterCount == 2 &&
              parameterTypes[0] == String::class.java
        }
            ?: return false
    val callbackType = method.parameterTypes[1]
    val callbackArg =
        if (callbackType.isAssignableFrom(ValueCallback::class.java)) {
          ValueCallback<String> { callback(it) }
        } else {
          Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { _, proxyMethod, args ->
            if (proxyMethod.name == "onReceiveValue") callback(args?.firstOrNull()?.toString())
            null
          }
        }
    return runCatching {
          method.isAccessible = true
          method.invoke(webView, code, callbackArg)
          true
        }
        .getOrElse {
          Log.ex(it)
          false
        }
  }

  private fun localResourcePath(url: String): String {
    val path = Uri.parse(url).path?.trimStart('/') ?: ""
    return if (path.length == 0) "frontend/index.html" else "frontend/${path}"
  }

  private fun localResourceResponse(url: String?): WebResourceResponse? {
    if (!isLocalChromeXtResource(url)) return null
    return runCatching {
          val path = localResourcePath(url!!)
          if (path.contains("..")) return null
          val ctx = Chrome.getContext()
          Resource.enrich(ctx)
          val bytes =
              if (path == "frontend/index.html") localFrontEndHtml().toByteArray()
              else ctx.assets.open(path).use { it.readBytes() }
          val mimeType = URLConnection.guessContentTypeFromName(path) ?: "text/plain"
          WebResourceResponse(mimeType, "UTF-8", ByteArrayInputStream(bytes)).apply {
            responseHeaders =
                mapOf(
                    "Cache-Control" to "no-store",
                    "Access-Control-Allow-Origin" to "*",
                    "X-Content-Type-Options" to "nosniff")
          }
        }
        .getOrElse {
          Log.ex(it)
          null
        }
  }

  private fun localFrontEndHtml(): String {
    val ctx = Chrome.getContext()
    val language = JSONObject.quote(Chrome.settings.getString("language", "system") ?: "system")
    Resource.enrich(ctx)
    val init =
        Local.initChromeXt +
            "\nglobalThis.ChromeXt = Symbol.ChromeXt;\n" +
            "globalThis.__ChromeXtLanguage = ${language};\n" +
            "//# sourceURL=local://ChromeXt/init"
    return ctx.assets
        .open("frontend/index.html")
        .bufferedReader()
        .use { it.readText() }
        .replace("<head>", "<head><script>${init}</script>")
  }

  private fun loadLocalFrontEnd(webView: Any, url: String?): Boolean {
    if (!isLocalChromeXtResource(url)) return false
    Chrome.updateTab(webView)
    loadedLocalFrontEnds[webView] = url
    injectedUrls.getOrPut(webView) { mutableSetOf() }.remove(url)
    injectedUrls.getOrPut(webView) { mutableSetOf() }.remove("https://chromext.local/")
    return false
  }

  override fun init() {

    findMethod(ChromeClient!!, true) { name == "onConsoleMessage" && parameterCount == 1 }
        // public boolean onConsoleMessage (ConsoleMessage consoleMessage)
        .hookAfter {
          // Don't use ConsoleMessage to specify this method since Mi Browser uses its own
          // implementation
          // This should be the way to communicate with the front-end of ChromeXt
          val chromeClient = it.thisObject!!
          val consoleMessage = it.args[0]!!
          val messageLevel = consoleMessage.invokeMethod { name == "messageLevel" }
          val sourceId = consoleMessage.invokeMethod { name == "sourceId" } as String
          val lineNumber = consoleMessage.invokeMethod { name == "lineNumber" }
          val message = consoleMessage.invokeMethod { name == "message" } as String
          if (messageLevel.toString() == "TIP" &&
              sourceId.startsWith("local://ChromeXt/init") &&
              lineNumber == Local.anchorInChromeXt) {
            val webView =
                records
                    .find {
                      if (Chrome.isQihoo) {
                            val mProvider = findField(WebView!!) { name == "mProvider" }
                            mProvider.get(it.get())
                          } else {
                            it.get()
                          }
                          ?.invokeMethod { name == "getWebChromeClient" } == chromeClient
                    }
                    ?.get()
            Listener.startAction(message, webView, chromeClient, sourceId)
          } else {
            Log.d(messageLevel.toString() + ": [${sourceId}@${lineNumber}] ${message}")
          }
        }

    fun onUpdateUrl(
        url: String,
        view: Any?,
        newNavigation: Boolean = false,
        force: Boolean = false
    ) {
      if (url.startsWith("javascript") || view == null) return
      runCatching {
            val recentUrlInjection = recentlyInjectedUrls[url]
            if (!force &&
                recentUrlInjection != null &&
                System.currentTimeMillis() - recentUrlInjection < reinjectionProbeCooldownMs) {
              return
            }
            if (!isLocalChromeXtResource(url)) loadedLocalFrontEnds.remove(view)
            if (newNavigation || force) injectedUrls.getOrPut(view) { mutableSetOf() }.remove(url)
            if (!injectedUrls.getOrPut(view) { mutableSetOf() }.add(url)) return
            val now = System.currentTimeMillis()
            injectedAt.getOrPut(view) { mutableMapOf() }[url] = now
            recentlyInjectedUrls[url] = now
            Chrome.updateTab(view)
            if (isLocalChromeXtResource(url)) return
            ScriptDbManager.invokeScript(url, view)
          }
          .onFailure { Log.ex(it) }
    }

    fun currentUrl(view: Any?): String? {
      return runCatching { view?.invokeMethod { name == "getUrl" } as? String }.getOrNull()
    }

    fun ensureScriptsInjected(view: Any?, delayMs: Long = 0, ignoreCooldown: Boolean = false) {
      if (view == null) return
      Handler(Chrome.getContext().mainLooper).postDelayed(
          {
            val url = currentUrl(view)
            if (url == null || url.startsWith("javascript")) return@postDelayed
            val lastInjected = injectedAt[view]?.get(url)
            if (!ignoreCooldown &&
                lastInjected != null &&
                System.currentTimeMillis() - lastInjected < reinjectionProbeCooldownMs) {
              return@postDelayed
            }
            val recentUrlInjection = recentlyInjectedUrls[url]
            if (!ignoreCooldown &&
                recentUrlInjection != null &&
                System.currentTimeMillis() - recentUrlInjection < reinjectionProbeCooldownMs) {
              return@postDelayed
            }
            runCatching {
                  val webSettings = view.invokeMethod { name == "getSettings" }
                  webSettings?.invokeMethod(true) { name == "setJavaScriptEnabled" }
                  val probe =
                      if (isChromeXtFrontEnd(url)) {
                        "!!(globalThis.ChromeXt && ChromeXt.dispatch)"
                      } else {
                        "globalThis.__ChromeXtInjected === true"
                  }
                  if (!evaluateJavascriptForResult(view, probe) { result ->
                    if (result != "true") {
                      onUpdateUrl(url, view, force = true)
                    }
                  }) {
                    onUpdateUrl(url, view, force = true)
                  }
                }
                .recoverCatching {
                  if (!evaluateJavascriptForResult(view, "void 0") {
                    onUpdateUrl(url, view, force = true)
                  }) {
                    onUpdateUrl(url, view, force = true)
                  }
                }
                .onFailure {
                  if (isChromeXtFrontEnd(url)) onUpdateUrl(url, view, force = true)
                }
          },
          delayMs)
    }

    fun ensureActivityWebViews(activity: Activity?, delayMs: Long = 0) {
      if (activity == null) return
      Handler(activity.mainLooper).postDelayed(
          {
            discoverWebViews(activity.window?.decorView).forEach {
              Chrome.updateTab(it)
              ensureScriptsInjected(it)
            }
          },
          delayMs)
    }

    fun hookViewClient(cls: Class<*>) {
      findMethodOrNull(cls, true) {
            name == "shouldInterceptRequest" &&
                parameterCount >= 2 &&
                WebResourceResponse::class.java.isAssignableFrom(returnType)
          }
          ?.takeIf { hookedViewClientMethods.add(it) }
          ?.hookMethod {
            before {
              runCatching {
                    val request = it.args[1]
                    val url =
                        if (request is String) request
                        else request?.invokeMethod { name == "getUrl" }?.toString()
                    localResourceResponse(url)?.let { response -> it.result = response }
                  }
                  .onFailure { error -> Log.ex(error) }
            }
          }

      findMethodOrNull(cls, true) { name == "onPageStarted" && parameterCount >= 2 }
          ?.takeIf { hookedViewClientMethods.add(it) }
          // public void onPageStarted (WebView view, String url, Bitmap favicon)
          ?.hookMethod {
            before {
              val url = it.args[1] as String
              if (loadLocalFrontEnd(it.args[0]!!, url)) it.result = null
            }
            after {
              if (Chrome.isQihoo && it.thisObject!!::class.java.declaredMethods.size > 1)
                return@after
              if (isLocalChromeXtResource(it.args[1] as String)) {
                injectedUrls.getOrPut(it.args[0]) { mutableSetOf() }.remove(it.args[1] as String)
                return@after
              }
              onUpdateUrl(it.args[1] as String, it.args[0], true)
            }
          }

      findMethodOrNull(cls, true) { name == "onReceivedError" && parameterCount >= 3 }
          ?.takeIf { hookedViewClientMethods.add(it) }
          ?.hookAfter {
            val request = it.args[1]
            val url =
                if (request is String) request
                else if (it.args.size > 3 && it.args[3] is String) it.args[3] as String
                else request?.invokeMethod { name == "getUrl" }?.toString()
            loadLocalFrontEnd(it.args[0]!!, url)
          }

      findMethodOrNull(cls, true) { name == "onPageCommitVisible" && parameterCount >= 2 }
          ?.takeIf { hookedViewClientMethods.add(it) }
          ?.hookAfter {
            val url = it.args[1] as String
            if (isChromeXtFrontEnd(url)) onUpdateUrl(url, it.args[0])
            else ensureScriptsInjected(it.args[0], 150, ignoreCooldown = true)
          }

      findMethodOrNull(cls, true) { name == "onPageFinished" && parameterCount >= 2 }
          ?.takeIf { hookedViewClientMethods.add(it) }
          ?.hookAfter {
            val url = it.args[1] as String
            if (isChromeXtFrontEnd(url)) onUpdateUrl(url, it.args[0])
            else ensureScriptsInjected(it.args[0], 150, ignoreCooldown = true)
          }
    }

    webViewClasses()
        .mapNotNull { findMethodOrNull(it, true) { name == "setWebChromeClient" } }
        .distinct()
        .forEach {
          it.hookAfter {
            val webView = it.thisObject
            records.removeAll(records.filter { it.get() == null || it.get() == webView })
            if (it.args[0] != null) records.add(WeakReference(webView))
          }
        }

    webViewClasses()
        .mapNotNull { cls ->
          findMethodOrNull(cls, true) {
            name == "loadUrl" && parameterCount >= 1 && parameterTypes[0] == String::class.java
          }
        }
        .distinct()
        .forEach {
          it.isAccessible = true
          it.hookMethod {
            before {
              val url = it.args[0] as String
              if (loadLocalFrontEnd(it.thisObject!!, url)) it.result = null
            }
          }
        }

    webViewClasses()
        .mapNotNull { findMethodOrNull(it, true) { name == "setWebViewClient" } }
        .distinct()
        .forEach { it.hookAfter { it.args[0]?.let { client -> hookViewClient(client::class.java) } } }

    webViewClasses()
        .mapNotNull { findMethodOrNull(it, true) { name == "onAttachedToWindow" } }
        .distinct()
        .forEach {
          it.hookAfter {
            Chrome.updateTab(it.thisObject)
            ensureScriptsInjected(it.thisObject, 150)
          }
        }

    hookViewClient(ViewClient!!)

    findMethod(Activity::class.java) { name == "onResume" }
        .hookAfter {
          ensureScriptsInjected(Chrome.getTab(), 250)
          ensureActivityWebViews(it.thisObject as? Activity, 300)
        }

    findMethod(Activity::class.java) { name == "onStop" }
        .hookBefore { ScriptDbManager.updateScriptStorage() }
    ensureActivityWebViews(Chrome.getContext() as? Activity, 300)
    ensureScriptsInjected(Chrome.getTab(), 500)
    isInit = true
  }
}
