package org.matrix.chromext.bridge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.matrix.chromext.ScriptTransferActivity
import org.matrix.chromext.ScriptTransferContract
import org.matrix.chromext.UiLocalization
import org.matrix.chromext.backup.ScriptTransferManager
import org.matrix.chromext.supportedPackages
import org.matrix.chromext.utils.Log

object BrowserBridgeHandshake {
  private val connectedChannels = ConcurrentHashMap.newKeySet<String>()
  const val ACTION_REQUEST = "org.matrix.chromext.action.BROWSER_BRIDGE_HANDSHAKE"
  const val PERMISSION = "org.matrix.chromext.permission.BROWSER_BRIDGE"
  const val EXTRA_BROWSER_BINDER = "org.matrix.chromext.extra.BROWSER_BINDER"
  const val EXTRA_CHANNEL = "org.matrix.chromext.extra.BRIDGE_CHANNEL"
  const val EXTRA_TRANSFER_ACTION = "org.matrix.chromext.extra.TRANSFER_ACTION"
  const val RESULT_REGISTER = 1
  const val RESULT_TRANSFER = 2

  fun request(context: Context, browserPackage: String) {
    if (browserPackage !in supportedPackages) return
    val acknowledged = AtomicBoolean(false)
    val handler = Handler(Looper.getMainLooper())
    val channel =
        object : ResultReceiver(handler) {
          override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
            when (resultCode) {
              RESULT_REGISTER -> {
                val browser =
                    IChromeXtBrowser.Stub.asInterface(resultData?.getBinder(EXTRA_BROWSER_BINDER))
                if (browser != null) {
                  acknowledged.set(true)
                  connectedChannels.add(browserPackage)
                  BrowserBridgeService.Registry.register(browserPackage, browser)
                  Log.i("Browser bridge handshake completed for $browserPackage")
                }
              }
              RESULT_TRANSFER -> {
                val action = resultData?.getString(EXTRA_TRANSFER_ACTION).orEmpty()
                handleTransfer(context.applicationContext, browserPackage, action)
              }
            }
          }
        }
    val request =
        Intent(ACTION_REQUEST).setPackage(browserPackage).putExtra(EXTRA_CHANNEL, channel)
    fun send(attempt: Int) {
      if (acknowledged.get()) return
      context.sendBroadcast(request)
      if (attempt < 2) {
        handler.postDelayed({ send(attempt + 1) }, 1_000L)
      }
    }
    send(0)
  }

  fun hasChannel(browserPackage: String): Boolean = browserPackage in connectedChannels

  fun forget(browserPackage: String) {
    connectedChannels.remove(browserPackage)
  }

  private fun handleTransfer(context: Context, packageName: String, action: String) {
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      val chinese = UiLocalization.isChinese(context)
      try {
        require(
            action == ScriptTransferContract.ACTION_IMPORT ||
                action == ScriptTransferContract.ACTION_EXPORT) { "无效的脚本数据操作" }
        val manager = ScriptTransferManager(context)
        if (!manager.hasDirectory()) {
          context.startActivity(
              Intent(context, ScriptTransferActivity::class.java)
                  .putExtra(ScriptTransferContract.EXTRA_ACTION, action)
                  .putExtra(ScriptTransferContract.EXTRA_BROWSER_PACKAGE, packageName)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          return@launch
        }
        val result =
            if (action == ScriptTransferContract.ACTION_EXPORT) {
              val file = manager.export(packageName)
              JSONObject(
                  mapOf(
                      "status" to "success",
                      "action" to action,
                      "count" to file.scriptCount,
                      "message" to
                          UiLocalization.text(
                              chinese,
                              "已导出到 ${file.name}",
                              "Exported to ${file.name}",
                          )))
            } else {
              val latest = manager.files().firstOrNull() ?: error("目录中没有脚本导出文件")
              val imported = manager.import(packageName, latest)
              JSONObject(
                  mapOf(
                      "status" to "success",
                      "action" to action,
                      "imported" to imported.imported,
                      "failed" to imported.failed,
                      "message" to
                          UiLocalization.text(
                              chinese,
                              "已导入 ${imported.imported} 个脚本 ${imported.failed} 个失败",
                              "Imported ${imported.imported} scripts; ${imported.failed} failed",
                          )))
            }
        BrowserBridgeService.Registry.request(packageName, "setTransferStatus", result.toString())
      } catch (error: Throwable) {
        val result =
            JSONObject(
                mapOf(
                    "status" to "error",
                    "action" to action,
                    "message" to
                        UiLocalization.error(
                            chinese,
                            error.message,
                            "脚本数据操作失败",
                            "Script data operation failed",
                        )))
        runCatching {
          BrowserBridgeService.Registry.request(packageName, "setTransferStatus", result.toString())
        }
      }
    }
  }
}
