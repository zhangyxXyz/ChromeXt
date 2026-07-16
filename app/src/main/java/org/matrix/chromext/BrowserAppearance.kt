package org.matrix.chromext

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamicColorScheme
import org.json.JSONObject

object BrowserAppearance {
  fun payload(context: Context, settings: SharedPreferences): JSONObject {
    val themeMode = settings.getString("ui_theme_mode", "System") ?: "System"
    val dark =
        when (themeMode) {
          "Dark" -> true
          "Light" -> false
          else -> settings.getBoolean("ui_theme_dark", false)
        }
    val dynamic = settings.getBoolean("ui_dynamic_color", true)
    val systemDynamicScheme =
        if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
          null
        }
    val configuredSeed = settings.getString("ui_theme_seed", "#6750A4") ?: "#6750A4"
    val seedColor =
        Color(
            runCatching { android.graphics.Color.parseColor(configuredSeed) }
                .getOrDefault(0xFF6750A4.toInt()))
    val scheme =
        systemDynamicScheme
            ?: dynamicColorScheme(seedColor = seedColor, isDark = dark, isAmoled = false)
    val seed = scheme.primary.toHex()
    return JSONObject().apply {
        put("themeMode", themeMode)
        put("dark", dark)
        put("dynamicColor", dynamic)
        put("seed", seed)
        put("liquidGlass", settings.getBoolean("ui_liquid_glass", false))
        put(
            "palette",
            JSONObject().apply {
              put("primary", scheme.primary.toHex())
              put("onPrimary", scheme.onPrimary.toHex())
              put("primaryContainer", scheme.primaryContainer.toHex())
              put("onPrimaryContainer", scheme.onPrimaryContainer.toHex())
              put("background", scheme.background.toHex())
              put("surface", scheme.surface.toHex())
              put("surfaceContainer", scheme.surfaceContainer.toHex())
              put("onSurface", scheme.onSurface.toHex())
              put("onSurfaceVariant", scheme.onSurfaceVariant.toHex())
              put("outline", scheme.outline.toHex())
            })
      }
  }

  private fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
}
