package org.matrix.chromext

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.matrix.chromext.utils.Log

const val TAG = "ChromeXt"

class OpenInChrome : Activity(), XposedServiceRepository.Listener {
  var defaultPackage = "com.android.chrome"
  private val handler = Handler(Looper.getMainLooper())
  private var handled = false
  private val connectionTimeout = Runnable { handleIntent(force = true) }

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
    XposedServiceRepository.initialize(this)
    XposedServiceRepository.addListener(this)
    handler.postDelayed(connectionTimeout, 3_000)
  }

  override fun onDestroy() {
    handler.removeCallbacks(connectionTimeout)
    XposedServiceRepository.removeListener(this)
    super.onDestroy()
  }

  override fun onStateChanged() {
    handleIntent(force = false)
  }

  private fun handleIntent(force: Boolean) {
    if (handled || (!force && !XposedServiceRepository.isServiceAvailable)) return
    handled = true
    handler.removeCallbacks(connectionTimeout)

    val availablePackages =
        XposedServiceRepository.getScope().filter { packageName ->
          packageName in supportedPackages &&
              runCatching {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0).applicationInfo?.enabled != false
                  }
                  .getOrDefault(false)
        }
    if (availablePackages.isEmpty()) {
      Log.toast(this, "No scoped supported browser found")
      finish()
      return
    } else {
      defaultPackage = availablePackages.first()
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
