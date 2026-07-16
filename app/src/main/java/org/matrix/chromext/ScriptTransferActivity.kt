package org.matrix.chromext

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.matrix.chromext.backup.ScriptTransferManager
import org.matrix.chromext.bridge.BrowserBridgeService
import org.matrix.chromext.utils.Log

class ScriptTransferActivity : AppCompatActivity() {
  private val directoryPicker =
      registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) {
          complete(
              JSONObject(
                  mapOf(
                      "status" to "cancelled",
                      "message" to
                          UiLocalization.text(
                              this,
                              "未选择脚本数据目录",
                              "No script data folder was selected",
                          ))))
        } else {
          performTransfer(uri)
        }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) directoryPicker.launch(null)
  }

  private fun performTransfer(uri: Uri) {
    val browserPackage = intent.getStringExtra(ScriptTransferContract.EXTRA_BROWSER_PACKAGE).orEmpty()
    val action = intent.getStringExtra(ScriptTransferContract.EXTRA_ACTION).orEmpty()
    val chinese = UiLocalization.isChinese(this)
    lifecycleScope.launch {
      val manager = ScriptTransferManager(this@ScriptTransferActivity)
      val result =
          runCatching {
                manager.setDirectory(uri)
                when (action) {
                  ScriptTransferContract.ACTION_EXPORT -> {
                    val file = manager.export(browserPackage)
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
                  }
                  ScriptTransferContract.ACTION_IMPORT -> {
                    val latest = manager.files().firstOrNull() ?: error("目录中没有脚本导出文件")
                    val imported = manager.import(browserPackage, latest)
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
                  else -> error("无效的脚本数据操作")
                }
              }
              .getOrElse {
                JSONObject(
                    mapOf(
                        "status" to "error",
                        "action" to action,
                        "message" to
                            UiLocalization.error(
                                chinese,
                                it.message,
                                "脚本数据操作失败",
                                "Script data operation failed",
                            )))
              }
      complete(result, browserPackage)
    }
  }

  private fun complete(result: JSONObject, browserPackage: String = intent.getStringExtra(ScriptTransferContract.EXTRA_BROWSER_PACKAGE).orEmpty()) {
    lifecycleScope.launch {
      if (browserPackage.isNotBlank()) {
        runCatching {
          BrowserBridgeService.Registry.request(browserPackage, "setTransferStatus", result.toString())
        }
      }
      Log.toast(this@ScriptTransferActivity, result.optString("message"))
      finish()
    }
  }
}
