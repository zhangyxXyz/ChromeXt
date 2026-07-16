package org.matrix.chromext.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.dynamicColorScheme

enum class ThemeMode {
  System,
  Light,
  Dark,
}

enum class ThemeColor(val argb: Long) {
  ShuiHong(0xFFF3D3E7),
  OuSe(0xFFEDD1D8),
  OuHe(0xFFE4C6D0),
  TuoYan(0xFFF9906F),
  YuDuBai(0xFFFCEFE8),
  JiangHuang(0xFFFFC773),
  YaSe(0xFFEEDEB0),
  XiangSe(0xFFF0C239),
  TuBai(0xFFF3F9F1),
  ShuiLv(0xFFD4F2E7),
  YueBai(0xFFD6ECF0),
  ShuiLan(0xFFD2F0F4),
  DanQing(0xFFD3E0F3),
  XueQing(0xFFB0A4E3),
  DingXiang(0xFFCCA4E3),
  Pink(0xFF984061),
  Magenta(0xFF8C4A60),
  JiangZi(0xFF8C4356),
  YanZhi(0xFF9D2933),
  Red(0xFFBA1A1A),
  Brown(0xFF77574E),
  XuanSe(0xFF622A1D),
  ZiTan(0xFF4C221B),
  Orange(0xFF8B5000),
  Yellow(0xFF6D5E00),
  Lime(0xFF526600),
  Green(0xFF356A35),
  SongHuaLv(0xFF057748),
  Teal(0xFF006A60),
  DaiLv(0xFF426666),
  TianShuiBi(0xFF5AA4AE),
  Cyan(0xFF006874),
  QunQing(0xFF4C8DAE),
  Blue(0xFF0061A4),
  ZangQing(0xFF2E4E7E),
  DaiLan(0xFF425066),
  Indigo(0xFF3F51B5),
  ZiSe(0xFF8D4BBB),
  Purple(0xFF6750A4),
  DaiZi(0xFF574266),
  Dai(0xFF4A4266),
  XuanQing(0xFF3D3B4F),
  CangSe(0xFF75878A),
  Slate(0xFF455A64),
  MoSe(0xFF50616D),
  YaQing(0xFF424C50),
}

data class AppearanceState(
    val themeMode: ThemeMode,
    val dynamicColor: Boolean,
    val themeColor: ThemeColor,
    val customThemeColor: String,
    val useCustomTheme: Boolean,
    val liquidGlass: Boolean,
)

@Composable
fun ChromeXtTheme(
    appearance: AppearanceState,
    onResolvedDarkChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val dark =
      when (appearance.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
      }
  val seed =
      if (appearance.useCustomTheme) {
        Color(
            runCatching { android.graphics.Color.parseColor(appearance.customThemeColor) }
                .getOrDefault(appearance.themeColor.argb.toInt()))
      } else {
        Color(appearance.themeColor.argb)
      }
  val colors =
      remember(appearance, dark, context) {
        when {
          appearance.dynamicColor && Build.VERSION.SDK_INT >= 31 && dark ->
              dynamicDarkColorScheme(context)
          appearance.dynamicColor && Build.VERSION.SDK_INT >= 31 ->
              dynamicLightColorScheme(context)
          else -> dynamicColorScheme(seedColor = seed, isDark = dark, isAmoled = false)
        }
      }
  LaunchedEffect(dark) { onResolvedDarkChange(dark) }
  MaterialTheme(colorScheme = colors, content = content)
}
