package org.matrix.chromext

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import org.matrix.chromext.ui.ChromeXtController
import org.matrix.chromext.ui.ChromeXtShell
import org.matrix.chromext.ui.ChromeXtTheme

class ScriptManagerActivity : AppCompatActivity() {
  private lateinit var controller: ChromeXtController

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    controller = ChromeXtController(this)
    controller.start()
    setContent {
      controller.ObserveAppearance { appearance ->
        ChromeXtTheme(appearance, controller::setResolvedThemeDark) {
          ChromeXtShell(controller)
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (::controller.isInitialized) controller.refresh()
  }

  override fun onDestroy() {
    if (::controller.isInitialized) controller.stop()
    super.onDestroy()
  }
}
