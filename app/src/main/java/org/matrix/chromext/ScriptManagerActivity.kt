package org.matrix.chromext

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.matrix.chromext.utils.Log

class ScriptManagerActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    openScriptManager()
    finish()
  }

  private fun openScriptManager() {
    val targetPackage =
        (miBrowserPackages + chromiumPackages).firstOrNull {
          runCatching { packageManager.getPackageInfo(it, 0) }.isSuccess
        }

    if (targetPackage == null) {
      Log.toast(this, "No supported browser installed")
      return
    }

    val managerUrl = "https://chromext.local/?from=module&ts=${System.currentTimeMillis()}"
    val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(managerUrl))
            .setPackage(targetPackage)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
        .onFailure { Log.toast(this, "Unable to open ChromeXt script manager") }
  }
}
