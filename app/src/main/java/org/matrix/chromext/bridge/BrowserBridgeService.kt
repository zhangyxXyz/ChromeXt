package org.matrix.chromext.bridge

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.matrix.chromext.supportedPackages

class BrowserBridgeService : Service() {
  private val binder =
      object : IChromeXtBridge.Stub() {
        override fun registerBrowser(packageName: String, browser: IChromeXtBrowser) {
          requireAllowedCaller(packageName)
          Registry.register(packageName, browser)
        }

      }

  override fun onBind(intent: Intent?): IBinder = binder

  private fun requireAllowedCaller(packageName: String) {
    val callerPackages = packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
    require(packageName in supportedPackages && packageName in callerPackages) {
      "Caller is not an allowed ChromeXt browser"
    }
  }

  object Registry {
    private data class Connection(
        val browser: IChromeXtBrowser,
        val deathRecipient: IBinder.DeathRecipient,
    )

    private val connections = ConcurrentHashMap<String, Connection>()
    private val listeners = ConcurrentHashMap.newKeySet<() -> Unit>()

    fun connectedPackages(): Set<String> = connections.keys.toSet()

    fun addListener(listener: () -> Unit) {
      listeners.add(listener)
      listener()
    }

    fun removeListener(listener: () -> Unit) {
      listeners.remove(listener)
    }

    fun register(packageName: String, browser: IChromeXtBrowser) {
      val binder = browser.asBinder()
      val recipient =
          IBinder.DeathRecipient {
            connections.computeIfPresent(packageName) { _, existing ->
              if (existing.browser.asBinder() === binder) null else existing
            }
            BrowserBridgeHandshake.forget(packageName)
            notifyListeners()
          }
      if (connections[packageName]?.browser?.asBinder() === binder) return
      runCatching { binder.linkToDeath(recipient, 0) }
      connections.put(packageName, Connection(browser, recipient))?.let { previous ->
        runCatching { previous.browser.asBinder().unlinkToDeath(previous.deathRecipient, 0) }
      }
      notifyListeners()
    }

    fun notifySettingsChanged() {
      connections.values.forEach { connection ->
        runCatching { connection.browser.settingsChanged() }
      }
    }

    suspend fun exportSnapshot(packageName: String, target: File): File =
        withContext(Dispatchers.IO) {
          val browser = connections[packageName]?.browser ?: error("目标浏览器尚未连接")
          target.parentFile?.mkdirs()
          val pipe = ParcelFileDescriptor.createPipe()
          try {
            browser.writeSnapshot(pipe[1])
            pipe[1].close()
            ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use { input ->
              target.outputStream().buffered().use(input::copyTo)
            }
          } catch (error: Throwable) {
            runCatching { pipe[0].close() }
            runCatching { pipe[1].close() }
            target.delete()
            throw error
          }
          require(target.length() > 0L) { "浏览器返回了空快照" }
          target
        }

    suspend fun restoreSnapshot(packageName: String, source: File) =
        withContext(Dispatchers.IO) {
          require(source.isFile) { "备份快照不存在" }
          val browser = connections[packageName]?.browser ?: error("目标浏览器尚未连接")
          val sourcePipe = ParcelFileDescriptor.createPipe()
          val resultPipe = ParcelFileDescriptor.createPipe()
          try {
            browser.restoreSnapshot(sourcePipe[0], resultPipe[1])
            sourcePipe[0].close()
            resultPipe[1].close()
            ParcelFileDescriptor.AutoCloseOutputStream(sourcePipe[1]).use { output ->
              source.inputStream().buffered().use { it.copyTo(output) }
            }
            val rawResult =
                ParcelFileDescriptor.AutoCloseInputStream(resultPipe[0]).bufferedReader().use {
                  it.readText()
                }
            val result = JSONObject(rawResult)
            result.optString("error")
                .takeIf(String::isNotBlank)
                ?.let { throw IllegalStateException(it) }
            require(result.optBoolean("ok")) { "浏览器未确认恢复完成" }
          } catch (error: Throwable) {
            runCatching { sourcePipe[0].close() }
            runCatching { sourcePipe[1].close() }
            runCatching { resultPipe[0].close() }
            runCatching { resultPipe[1].close() }
            throw error
          }
        }

    suspend fun request(packageName: String, action: String, payload: String = ""): String =
        withContext(Dispatchers.IO) {
          val browser = connections[packageName]?.browser ?: error("目标浏览器尚未连接")
          val pipe = ParcelFileDescriptor.createPipe()
          try {
            browser.writeResponse(action, payload, pipe[1])
            pipe[1].close()
            ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).bufferedReader().use { it.readText() }
          } catch (error: Throwable) {
            runCatching { pipe[0].close() }
            runCatching { pipe[1].close() }
            throw error
          }
        }

    suspend fun requestFile(packageName: String, action: String, payload: File): String =
        withContext(Dispatchers.IO) {
          require(payload.isFile) { "脚本数据文件不存在" }
          val browser = connections[packageName]?.browser ?: error("目标浏览器尚未连接")
          val source = ParcelFileDescriptor.open(payload, ParcelFileDescriptor.MODE_READ_ONLY)
          val resultPipe = ParcelFileDescriptor.createPipe()
          try {
            browser.writeStreamResponse(action, source, resultPipe[1])
            source.close()
            resultPipe[1].close()
            ParcelFileDescriptor.AutoCloseInputStream(resultPipe[0]).bufferedReader().use {
              it.readText()
            }
          } catch (error: Throwable) {
            runCatching { source.close() }
            runCatching { resultPipe[0].close() }
            runCatching { resultPipe[1].close() }
            throw error
          }
        }

    private fun notifyListeners() {
      listeners.forEach { listener -> runCatching(listener) }
    }
  }
}
