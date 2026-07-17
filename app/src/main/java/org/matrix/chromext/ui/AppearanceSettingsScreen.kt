package org.matrix.chromext.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.matrix.chromext.UiLocalization

@Composable
fun AppearanceSettingsScreen(controller: ChromeXtController) {
  controller.revision
  val chinese = controller.isChinese
  val appearance = controller.appearance()
  var modeDialog by remember { mutableStateOf(false) }
  var customPicker by remember { mutableStateOf(false) }
  val previewDark = MaterialTheme.colorScheme.background.luminance() < .5f

  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      AppearanceContainer(ap(chinese, "外观", "Appearance")) {
        AppearanceClickRow(
            Icons.Rounded.Contrast,
            ap(chinese, "主题模式", "Theme mode"),
            when (appearance.themeMode) {
              ThemeMode.System -> ap(chinese, "跟随系统", "System")
              ThemeMode.Light -> ap(chinese, "浅色", "Light")
              ThemeMode.Dark -> ap(chinese, "深色", "Dark")
            },
        ) {
          modeDialog = true
        }
        AppearanceSwitchRow(
            Icons.Rounded.ColorLens,
            ap(chinese, "动态颜色", "Dynamic color"),
            ap(chinese, "Android 12 及以上使用当前系统配色", "Use the current system palette on Android 12+"),
            appearance.dynamicColor,
            controller::setDynamicColor,
        )
        AppearanceSwitchRow(
            Icons.Rounded.BlurOn,
            ap(chinese, "液态玻璃导航栏", "Liquid glass navigation"),
            ap(chinese, "使用模板同款模糊、折射和拖动效果", "Use the template blur, lens, and drag effects"),
            appearance.liquidGlass,
            controller::setLiquidGlass,
        )
        Text(
            ap(chinese, "主题颜色", "Theme color"),
            Modifier.padding(start = 20.dp, top = 12.dp),
            fontWeight = FontWeight.Medium,
        )
        Text(
            ap(chinese, "选择颜色会自动关闭动态颜色", "Selecting a color disables dynamic color"),
            Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ThemeColorPreviewRow(
            ThemeColor.entries,
            appearance.themeColor,
            appearance.useCustomTheme,
            appearance.dynamicColor,
            previewDark,
            controller::setThemeColor,
        )
        val customPreview =
            remember(appearance.customThemeColor, previewDark) {
              previewColors(
                  runCatching { Color(AndroidColor.parseColor(appearance.customThemeColor)) }
                      .getOrDefault(Color(0xFF6750A4)),
                  previewDark)
            }
        Row(
            Modifier.fillMaxWidth()
                .clickable { customPicker = true }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          PalettePreview(
              customPreview,
              selected = appearance.useCustomTheme && !appearance.dynamicColor)
          Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(ap(chinese, "自定义颜色", "Custom color"), fontWeight = FontWeight.Medium)
            Text(
                ap(chinese, "使用 HSV 取色器选择主题种子色", "Choose a theme seed with the HSV picker"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                appearance.customThemeColor.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
          }
          Icon(Icons.Rounded.Colorize, null)
        }
      }
    }
  }

  if (modeDialog) {
    val modes = ThemeMode.entries
    val labels =
        modes.map { mode ->
          when (mode) {
            ThemeMode.System -> ap(chinese, "跟随系统", "System")
            ThemeMode.Light -> ap(chinese, "浅色", "Light")
            ThemeMode.Dark -> ap(chinese, "深色", "Dark")
          }
        }
    val descriptions =
        listOf(
            ap(chinese, "随系统自动切换浅色与深色", "Switch with the system appearance"),
            ap(chinese, "始终使用明亮的界面配色", "Always use the light color scheme"),
            ap(chinese, "始终使用低亮度深色配色", "Always use the dark color scheme"),
        )
    val icons =
        listOf(
            Icons.Rounded.SettingsBrightness, Icons.Rounded.LightMode, Icons.Rounded.DarkMode)
    AlertDialog(
        onDismissRequest = { modeDialog = false },
        icon = { Icon(Icons.Rounded.Contrast, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(ap(chinese, "主题模式", "Theme mode")) },
        dismissButton = {
          TextButton(onClick = { modeDialog = false }) { Text(ap(chinese, "取消", "Cancel")) }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            modes.forEachIndexed { index, mode ->
              val selected = appearance.themeMode == mode
              Surface(
                  onClick = {
                    controller.setThemeMode(mode)
                    modeDialog = false
                  },
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(18.dp),
                  color =
                      if (selected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceContainer,
                  border =
                      BorderStroke(
                          1.dp,
                          if (selected) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.outlineVariant),
              ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                  Surface(
                      shape = RoundedCornerShape(13.dp),
                      color =
                          if (selected) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.surfaceContainerHighest,
                  ) {
                    Icon(
                        icons[index],
                        null,
                        Modifier.padding(10.dp).size(22.dp),
                        tint =
                            if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        labels[index],
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        descriptions[index],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  if (selected) {
                    Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                  }
                }
              }
            }
          }
        },
        confirmButton = {})
  }
  if (customPicker) {
    HsvColorDialog(
        appearance.customThemeColor,
        chinese,
        dismiss = { customPicker = false },
    ) {
      controller.setCustomThemeColor(it)
      customPicker = false
    }
  }
}

@Composable
private fun AppearanceContainer(title: String, content: @Composable ColumnScope.() -> Unit) {
  Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
          Text(
              title,
              Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold)
          content()
        }
      }
}

@Composable
private fun AppearanceClickRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
  Row(
      Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically) {
        AppearanceIcon(icon)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
          Text(title)
          Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
}

@Composable
private fun AppearanceSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
  Row(
      Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically) {
        AppearanceIcon(icon)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
          Text(title)
          Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
      }
}

@Composable
private fun AppearanceIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
  Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
    Icon(icon, null, Modifier.padding(9.dp).size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
  }
}

@Composable
private fun ThemeColorPreviewRow(
    colors: List<ThemeColor>,
    selected: ThemeColor,
    useCustomTheme: Boolean,
    dynamicColor: Boolean,
    previewDark: Boolean,
    onSelect: (ThemeColor) -> Unit,
) {
  Row(
      Modifier.fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { color ->
          val preview = remember(color, previewDark) { previewColors(Color(color.argb), previewDark) }
          PalettePreview(
              preview,
              selected = !dynamicColor && !useCustomTheme && selected == color,
              modifier = Modifier.clickable { onSelect(color) })
        }
      }
}

@Composable
private fun PalettePreview(
    colors: Triple<Color, Color, Color>,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier
          .size(64.dp, 38.dp)
          .background(Color.Transparent, RoundedCornerShape(19.dp))
          .then(
              if (selected) {
                Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(19.dp))
              } else {
                Modifier
              }),
      contentAlignment = Alignment.Center) {
        Box(Modifier.offset(x = (-18).dp).size(22.dp).background(colors.first.copy(alpha = .82f), CircleShape))
        Box(Modifier.size(22.dp).background(colors.second.copy(alpha = .82f), CircleShape))
        Box(Modifier.offset(x = 18.dp).size(22.dp).background(colors.third.copy(alpha = .82f), CircleShape))
        if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = Color.White)
      }
}

@Composable
private fun HsvColorDialog(
    initial: String,
    chinese: Boolean,
    dismiss: () -> Unit,
    select: (String) -> Unit,
) {
  val parsed = runCatching { AndroidColor.parseColor(initial) }.getOrDefault(AndroidColor.GRAY)
  val initialHsv = remember(initial) { FloatArray(3).also { AndroidColor.colorToHSV(parsed, it) } }
  var hue by remember(initial) { mutableFloatStateOf(initialHsv[0]) }
  var saturation by remember(initial) { mutableFloatStateOf(initialHsv[1]) }
  var brightness by remember(initial) { mutableFloatStateOf(initialHsv[2]) }
  val selectedArgb = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
  val hex = "#%06X".format(0xFFFFFF and selectedArgb)
  val hueColors =
      remember {
        (0..12).map { Color(AndroidColor.HSVToColor(floatArrayOf(it * 30f, 1f, 1f))) }
      }
  AlertDialog(
      onDismissRequest = dismiss,
      title = { Text(ap(chinese, "选择颜色", "Choose color")) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Canvas(
              Modifier.fillMaxWidth().height(220.dp).pointerInput(hue) {
                detectTapGestures { point ->
                  saturation = (point.x / size.width).coerceIn(0f, 1f)
                  brightness = (1f - point.y / size.height).coerceIn(0f, 1f)
                }
              }) {
                val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val marker = Offset(size.width * saturation, size.height * (1f - brightness))
                drawCircle(Color.White, 10.dp.toPx(), marker)
                drawCircle(Color.Black, 7.dp.toPx(), marker, style = Stroke(2.dp.toPx()))
              }
          Canvas(
              Modifier.fillMaxWidth().height(34.dp).pointerInput(Unit) {
                detectTapGestures { point ->
                  hue = (point.x / size.width * 360f).coerceIn(0f, 359.9f)
                }
              }) {
                drawRoundRect(
                    Brush.horizontalGradient(hueColors),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
                val x = size.width * hue / 360f
                drawCircle(Color.White, 9.dp.toPx(), Offset(x, size.height / 2))
                drawCircle(Color.Black, 7.dp.toPx(), Offset(x, size.height / 2), style = Stroke(2.dp.toPx()))
              }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(42.dp), shape = RoundedCornerShape(12.dp), color = Color(selectedArgb)) {}
            Column(Modifier.padding(start = 12.dp)) {
              Text(hex, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text(
                  "H ${hue.toInt()}° · S ${(saturation * 100).toInt()}% · B ${(brightness * 100).toInt()}%",
                  style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      },
      dismissButton = { TextButton(dismiss) { Text(ap(chinese, "取消", "Cancel")) } },
      confirmButton = { Button({ select(hex) }) { Text(ap(chinese, "确定", "OK")) } })
}

private fun previewColors(seed: Color, dark: Boolean): Triple<Color, Color, Color> {
  val hsv = FloatArray(3)
  AndroidColor.colorToHSV(seed.toArgb(), hsv)
  fun shifted(degrees: Float, saturationScale: Float, valueScale: Float): Color {
    val next =
        floatArrayOf(
            (hsv[0] + degrees + 360f) % 360f,
            (hsv[1] * saturationScale).coerceIn(.25f, 1f),
            (hsv[2] * valueScale).coerceIn(if (dark) .62f else .42f, if (dark) 1f else .88f))
    return Color(AndroidColor.HSVToColor(next))
  }
  return Triple(
      shifted(0f, 1f, if (dark) 1.18f else .92f),
      shifted(24f, .72f, if (dark) 1.08f else .82f),
      shifted(-32f, .62f, if (dark) 1.12f else .86f))
}

private fun ap(chinese: Boolean, zh: String, en: String): String =
    UiLocalization.text(chinese, zh, en)
