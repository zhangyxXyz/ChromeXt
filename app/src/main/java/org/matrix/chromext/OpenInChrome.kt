package org.matrix.chromext

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.matrix.chromext.utils.Log

const val TAG = "ChromeXt"

class OpenInChrome : Activity() {
  var defaultPackage = "com.android.chrome"

  fun invokeChromeTabbed(url: String) {
    val activity = "com.google.android.apps.chrome.Main"
    val chromeMain =
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName(defaultPackage, activity))
    startActivity(chromeMain.putExtra("ChromeXt", url))
  }

  private fun openUrl(url: String, destination: ComponentName?) {
    if (destination == null) {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(defaultPackage))
    } else {
      invokeChromeTabbed(url)
    }
  }

  @Suppress("QueryPermissionsNeeded")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    @Suppress("DEPRECATION") val installedApplications = packageManager.getInstalledApplications(0)
    val avaiblePackages = supportedPackages.intersect(installedApplications.map { it.packageName })
    if (avaiblePackages.size == 0) {
      Log.toast(this, "No supported Chrome installed")
      finish()
      return
    } else {
      defaultPackage = avaiblePackages.last()
    }

    val isSamsung = defaultPackage.startsWith("com.sec.android.app.sbrowser")
    val isMi = miBrowserPackages.contains(defaultPackage)
    val intent: Intent = getIntent()
    val destination: ComponentName? =
        if (isMi) {
          null
        } else {
          ComponentName(
              defaultPackage,
              if (isSamsung) {
                "com.sec.android.app.sbrowser.SBrowserMainActivity"
              } else {
                "com.google.android.apps.chrome.IntentDispatcher"
              })
        }

    if (intent.action == Intent.ACTION_VIEW) {
      if (destination == null) intent.setPackage(defaultPackage) else intent.setComponent(destination)
      intent.setDataAndType(intent.data, "text/html")
      startActivity(intent)
    } else if (intent.action == Intent.ACTION_SEND && !isSamsung) {
      var text = intent.getStringExtra(Intent.EXTRA_TEXT)
      if (text == null || intent.type != "text/plain") {
        finish()
        return
      }

      Log.d("Get share text: ${text}")
      if (text.startsWith("file://") || text.startsWith("data:")) {
        openUrl(text, destination)
      } else {
        if (!text.contains("://")) {
          text = "https://google.com/search?q=${text.replace("#", "%23")}"
        } else if (text.contains("\n ")) {
          text = text.split("\n ")[1]
        }

        if (!text.startsWith("http")) {
          // Unable to open custom url
          Log.toast(this, "Unable to open ${text.split("://").first()} scheme")
          finish()
          return
        }

        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(text)).apply {
              if (destination == null) setPackage(defaultPackage) else component = destination
            })
      }
    }
    finish()
  }
}
