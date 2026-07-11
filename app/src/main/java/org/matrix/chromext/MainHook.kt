package org.matrix.chromext

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.matrix.chromext.hook.BaseHook
import org.matrix.chromext.hook.ContextMenuHook
import org.matrix.chromext.hook.PageInfoHook
import org.matrix.chromext.hook.PageMenuHook
import org.matrix.chromext.hook.PreferenceHook
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.ModernXposed
import org.matrix.chromext.utils.Unhook
import org.matrix.chromext.utils.findMethodOrNull
import org.matrix.chromext.utils.hookAfter

class MainHook : XposedModule() {
  private lateinit var processName: String
  private var miContextReady = false
  private var miApplicationAttachHook: Unhook? = null

  override fun onModuleLoaded(param: ModuleLoadedParam) {
    ModernXposed.module = this
    if (frameworkProperties and XposedInterface.PROP_CAP_REMOTE == 0L) {
      Log.e("$frameworkName does not support remote preferences; detaching ChromeXt")
      detach()
      return
    }
    ModernXposed.settings = getRemotePreferences("ChromeXt")
    processName = param.processName
    Resource.init(moduleApplicationInfo.sourceDir)
    Log.d("$processName started with $frameworkName $frameworkVersion (API $apiVersion)")
  }

  override fun onPackageReady(param: PackageReadyParam) {
    val packageName = param.packageName
    if (!supportedPackages.contains(packageName)) return
    if (chromiumPackages.contains(packageName)) {
      param.classLoader
          .loadClass("org.chromium.ui.base.WindowAndroid")
          .declaredConstructors[1]
          .hookAfter {
            Chrome.init(it.args[0] as Context, packageName)
            initHooks(UserScriptHook)
            if (ContextMenuHook.isInit) return@hookAfter
            runCatching {
                  if (!Chrome.isVivaldi) initHooks(PreferenceHook)
                  initHooks(if (Chrome.isEdge || Chrome.isCocCoc) PageInfoHook else PageMenuHook)
                }
                .onFailure {
                  initHooks(ContextMenuHook)
                  if (BuildConfig.DEBUG && !(Chrome.isSamsung || Chrome.isEdge)) Log.ex(it)
                }
          }
      detach()
    } else {
      Chrome.isMi = Chrome.isMi || miBrowserPackages.contains(packageName)
      Chrome.isQihoo = packageName == "com.qihoo.contents"

      if (Chrome.isMi && !miContextReady) {
        miApplicationAttachHook =
            ContextWrapper::class.java
                .getDeclaredMethod("attachBaseContext", Context::class.java)
                .hookAfter {
          if (it.thisObject !is Application) return@hookAfter
          miApplicationAttachHook?.unhook()
          miApplicationAttachHook = null
          miContextReady = true
          Chrome.init(it.thisObject as Context, packageName)
          onPackageReady(param)
        }
        return
      }

      if (Chrome.isMi) {
        WebViewHook.WebView =
            runCatching { Chrome.load("hyper.webkit.WebView") }
                .getOrElse { Chrome.load("com.miui.webkit.WebView") }
        runCatching {
          WebViewHook.extraWebViews.add(Chrome.load("miui.browser.webview.BrowserWebView"))
        }
        runCatching {
              WebViewHook.ViewClient = Chrome.load("miui.browser.webview.o")
              WebViewHook.ChromeClient = Chrome.load("com.android.browser.Tab\$MainWebChromeClient")
            }
            .recoverCatching {
              WebViewHook.ViewClient = Chrome.load("com.android.browser.Tab\$MainWebViewClient")
              WebViewHook.ChromeClient = Chrome.load("com.android.browser.Tab\$MainWebChromeClient")
            }
            .recoverCatching {
              WebViewHook.ViewClient = Chrome.load("com.android.browser.tab.TabWebViewClient")
              WebViewHook.ChromeClient = Chrome.load("com.android.browser.tab.TabWebChromeClient")
            }
            .onFailure {
              val miuiAutologinBar = Chrome.load("com.android.browser.MiuiAutologinBar")
              // Use MiuiAutologinBar to find `com.android.browser.tab.Tab`, which can located by
              // searching the string "X-MiOrigin"
              val fields = miuiAutologinBar.declaredFields.map { it.type }
              val tab =
                  miuiAutologinBar.declaredMethods
                      .find {
                        it.parameterCount == 2 &&
                            it.parameterTypes[1] == Boolean::class.java &&
                            !fields.contains(it.parameterTypes[0])
                      }!!
                      .run { parameterTypes[0] }
              tab.declaredFields.forEach {
                if (
                    findMethodOrNull(it.type) {
                      // Found by searching the string "Console: "
                      it.name == "onGeolocationPermissionsHidePrompt"
                    } != null
                )
                    WebViewHook.ChromeClient = it.type
                if (
                    findMethodOrNull(it.type) {
                      // Found by searching the string "Tab.MainWebViewClient"
                      it.name == "onReceivedHttpAuthRequest"
                    } != null
                )
                    WebViewHook.ViewClient = it.type
              }
            }

        hookWebView()
        detach()
        return
      }

      if (Chrome.isQihoo) {
        WebViewHook.WebView = Chrome.load("com.qihoo.webkit.WebView")
        WebViewHook.ViewClient = Chrome.load("com.qihoo.webkit.WebViewClient")
        WebViewHook.ChromeClient = Chrome.load("com.qihoo.webkit.WebChromeClient")
        hookWebView()
        detach()
        return
      }

      WebViewClient::class.java.declaredConstructors[0].hookAfter {
        if (it.thisObject!!::class != WebViewClient::class) {
          WebViewHook.ViewClient = it.thisObject!!::class.java
          hookWebView()
        }
      }

      WebChromeClient::class.java.declaredConstructors[0].hookAfter {
        if (it.thisObject!!::class != WebChromeClient::class) {
          WebViewHook.ChromeClient = it.thisObject!!::class.java
          hookWebView()
        }
      }
      detach()
    }
  }

  private fun hookWebView() {
    if (WebViewHook.ChromeClient == null || WebViewHook.ViewClient == null) return
    // Xiaomi uses a vendor WebView wrapper, but its Chromium DevTools endpoint is still gated by
    // the platform WebView debugging flag. Enable it before any CSP-bypass session is requested.
    runCatching { WebView.setWebContentsDebuggingEnabled(true) }
        .onFailure { if (BuildConfig.DEBUG) Log.ex(it) }
    runCatching {
          WebViewHook.WebView?.let { webViewClass ->
            findMethodOrNull(webViewClass, true) {
                  name == "setWebContentsDebuggingEnabled" &&
                      parameterCount == 1 &&
                      parameterTypes[0] == Boolean::class.javaPrimitiveType
                }
                ?.invoke(null, true)
          }
        }
        .onFailure { if (BuildConfig.DEBUG) Log.ex(it) }
    if (WebViewHook.WebView == null) {
      WebViewHook.WebView = WebView::class.java
    }
    initHooks(WebViewHook, ContextMenuHook)
  }

  private fun initHooks(vararg hook: BaseHook) {
    hook.forEach {
      if (it.isInit) return@forEach
      it.init()
      if (it.isInit) Log.d("${it.javaClass.simpleName} hooked")
    }
  }
}
