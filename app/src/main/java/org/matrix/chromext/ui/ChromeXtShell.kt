package org.matrix.chromext.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BrowserUpdated
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Http
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.matrix.chromext.BuildConfig
import org.matrix.chromext.UiLocalization
import org.matrix.chromext.backup.BackupManager
import org.matrix.chromext.backup.ScriptTransferManager
import org.matrix.chromext.ui.common.NavigationSettingItem
import org.matrix.chromext.ui.common.SettingGroup
import org.matrix.chromext.ui.common.SettingItem
import org.matrix.chromext.ui.common.SettingLeadingIcon
import org.matrix.chromext.ui.common.SwitchSettingItem
import org.matrix.chromext.ui.glass.LiquidGlassBottomBar
import org.matrix.chromext.ui.glass.LiquidGlassBottomBarItem

enum class MainTab {
  Home,
  Scripts,
  Settings,
}

enum class SettingsPage {
  Root,
  Appearance,
  Language,
  Backup,
  About,
  ReleaseHistory,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChromeXtShell(controller: ChromeXtController) {
  controller.revision
  val chinese = controller.isChinese
  val appearance = controller.appearance()
  val surfaceColor = MaterialTheme.colorScheme.surface
  val backdrop = rememberLayerBackdrop {
    drawRect(surfaceColor)
    drawContent()
  }
  BackHandler(enabled = controller.settingsPage != SettingsPage.Root) {
    controller.settingsPage =
        if (controller.settingsPage == SettingsPage.ReleaseHistory) SettingsPage.About
        else SettingsPage.Root
  }
  BackHandler(
      enabled = controller.settingsPage == SettingsPage.Root && controller.currentTab != MainTab.Home) {
        controller.currentTab = MainTab.Home
      }

  val title =
      when (controller.settingsPage) {
        SettingsPage.Appearance -> tx(chinese, "外观", "Appearance")
        SettingsPage.Language -> tx(chinese, "语言", "Language")
        SettingsPage.Backup -> tx(chinese, "备份与恢复", "Backup and restore")
        SettingsPage.About -> tx(chinese, "关于", "About")
        SettingsPage.ReleaseHistory -> tx(chinese, "更新历史", "Release history")
        SettingsPage.Root ->
            when (controller.currentTab) {
              MainTab.Home -> "ChromeXt"
              MainTab.Scripts -> tx(chinese, "脚本", "Scripts")
              MainTab.Settings -> tx(chinese, "设置", "Settings")
            }
      }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
              if (controller.settingsPage != SettingsPage.Root) {
                IconButton(
                    onClick = {
                      controller.settingsPage =
                          if (controller.settingsPage == SettingsPage.ReleaseHistory) {
                            SettingsPage.About
                          } else {
                            SettingsPage.Root
                          }
                    }) {
                  Icon(
                      Icons.AutoMirrored.Rounded.ArrowBack,
                      tx(chinese, "返回", "Back"),
                  )
                }
              }
            },
            actions = {
              if (controller.currentTab == MainTab.Home &&
                  controller.settingsPage == SettingsPage.Root &&
                  !controller.isConnected) {
                IconButton(onClick = controller::retryConnection) {
                  Icon(Icons.Rounded.Refresh, tx(chinese, "重新连接", "Retry"))
                }
              }
            })
      },
      bottomBar = {
        if (controller.settingsPage == SettingsPage.Root) {
          ChromeXtBottomBar(
              selected = controller.currentTab,
              liquidGlass = appearance.liquidGlass,
              backdrop = backdrop,
              chinese = chinese,
              onSelected = { controller.currentTab = it },
          )
        }
      }) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).then(
                if (appearance.liquidGlass) Modifier.layerBackdrop(backdrop) else Modifier)) {
          when (controller.settingsPage) {
            SettingsPage.Appearance -> AppearanceSettingsScreen(controller)
            SettingsPage.Language -> LanguageSettingsScreen(controller)
            SettingsPage.Backup -> BackupLandingScreen(controller)
            SettingsPage.About -> AboutSettingsScreen(controller)
            SettingsPage.ReleaseHistory -> ReleaseHistoryScreen(controller)
            SettingsPage.Root ->
                when (controller.currentTab) {
                  MainTab.Home -> HomeScreen(controller)
                  MainTab.Scripts -> ScriptsScreen(controller)
                  MainTab.Settings -> SettingsScreen(controller)
                }
          }
        }
      }

  if (controller.browserPickerVisible) {
    BrowserManagerPicker(controller)
  }
}

@Composable
private fun ChromeXtBottomBar(
    selected: MainTab,
    liquidGlass: Boolean,
    backdrop: Backdrop,
    chinese: Boolean,
    onSelected: (MainTab) -> Unit,
) {
  if (liquidGlass) {
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
      LiquidGlassBottomBar(
          selectedIndex = selected.ordinal,
          onSelected = { onSelected(MainTab.entries[it]) },
          backdrop = backdrop,
          tabsCount = MainTab.entries.size,
      ) {
        MainTab.entries.forEach { tab ->
          val label = tabLabel(tab, chinese)
          LiquidGlassBottomBarItem(
              modifier = Modifier.defaultMinSize(minWidth = 58.dp),
              onClick = { if (selected != tab) onSelected(tab) },
          ) {
            Icon(tabIcon(tab), label, tint = MaterialTheme.colorScheme.onSurface)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  } else {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer) {
          MainTab.entries.forEach { tab ->
            val label = tabLabel(tab, chinese)
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                icon = { Icon(tabIcon(tab), label) },
                label = { Text(label) },
                alwaysShowLabel = false,
            )
          }
        }
  }
}

private fun tabLabel(tab: MainTab, chinese: Boolean) =
    when (tab) {
      MainTab.Home -> tx(chinese, "首页", "Home")
      MainTab.Scripts -> tx(chinese, "脚本", "Scripts")
      MainTab.Settings -> tx(chinese, "设置", "Settings")
    }

private fun tabIcon(tab: MainTab): ImageVector =
    when (tab) {
      MainTab.Home -> Icons.Rounded.Home
      MainTab.Scripts -> Icons.Rounded.Code
      MainTab.Settings -> Icons.Rounded.Settings
    }

@Composable
private fun HomeScreen(controller: ChromeXtController) {
  val revision = controller.revision
  val chinese = controller.isChinese
  val transferManager = remember { ScriptTransferManager(controller.context) }
  var exportLocation by remember { mutableStateOf(transferManager.storageLocation()) }
  LaunchedEffect(revision) { exportLocation = transferManager.storageLocation() }
  val exportDirectoryPicker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { transferManager.setDirectory(uri) }
            .onSuccess {
              exportLocation = transferManager.storageLocation()
              controller.refresh()
              Toast.makeText(
                      controller.context,
                      tx(chinese, "脚本导出目录已设置", "Script export folder updated"),
                      Toast.LENGTH_SHORT)
                  .show()
            }
            .onFailure { failure ->
              Toast.makeText(
                      controller.context,
                      UiLocalization.error(
                          chinese,
                          failure.localizedMessage,
                          "目录设置失败",
                          "Could not set folder"),
                      Toast.LENGTH_LONG)
                  .show()
            }
      }
  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(18.dp, 10.dp, 18.dp, 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      StatusHero(
          connected = controller.isConnected,
          title =
              if (controller.isConnected) tx(chinese, "模块服务已连接", "Module service connected")
              else tx(chinese, "等待模块服务", "Waiting for module service"),
          detail =
              if (controller.isConnected) {
                tx(chinese, "远程设置和作用域可用", "Remote settings and scope are available")
              } else {
                controller.connectionError
                    ?: tx(chinese, "请确认已在 LSPosed 中启用 ChromeXt", "Enable ChromeXt in LSPosed")
              },
      )
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DashboardCard(
            title = tx(chinese, "作用域浏览器", "Scoped browsers"),
            value = controller.browserTargets.size.toString(),
            icon = Icons.Rounded.Security,
            modifier = Modifier.weight(1f),
        )
        DashboardCard(
            title = tx(chinese, "已连接浏览器", "Connected browsers"),
            value =
                if (controller.isConnected) {
                  controller.connectedBrowserTargets().size.toString()
                } else {
                  tx(chinese, "不可用", "Unavailable")
                },
            icon = Icons.Rounded.BrowserUpdated,
            modifier = Modifier.weight(1f),
        )
      }
    }
    item {
      SectionCard(tx(chinese, "脚本与网页", "Scripts and pages")) {
        ActionRow(
            Icons.Rounded.Code,
            tx(chinese, "打开脚本管理", "Open script manager"),
            tx(chinese, "在已配置作用域的浏览器中管理用户脚本", "Manage userscripts in a scoped browser"),
        ) {
          controller.openScriptManager()
        }
        SwitchRow(
            Icons.Rounded.TouchApp,
            tx(chinese, "开启悬浮入口", "Enable floating launcher"),
            tx(chinese, "在网页边缘显示运行时脚本面板入口", "Show the runtime panel launcher on page edges"),
            controller.runtimeLauncherEnabled(),
            controller::setRuntimeLauncherEnabled,
        )
        SwitchRow(
            Icons.Rounded.Http,
            tx(chinese, "本地 HTTP 服务", "Local HTTP server"),
            tx(chinese, "Chrome 推荐开启\n小米浏览器通常无需开启", "Recommended for Chrome\nUsually unnecessary for Mi Browser"),
            controller.localServerEnabled(),
            controller::setLocalServerEnabled,
        )
      }
    }
    item {
      SectionCard(tx(chinese, "数据", "Data")) {
        ActionRow(
            Icons.Rounded.FolderOpen,
            tx(chinese, "脚本导出目录", "Script export folder"),
            if (exportLocation.configured) {
              tx(
                  chinese,
                  "${exportLocation.displayPath} · ${if (exportLocation.isDefault) "默认目录" else "自定义目录"} · 自动按浏览器包名分目录",
                  "${exportLocation.displayPath} · ${if (exportLocation.isDefault) "Default folder" else "Custom folder"} · Browser package subfolders are created automatically")
            } else {
              tx(
                  chinese,
                  "未设置 · 建议默认：${exportLocation.displayPath} · 自动按浏览器包名分目录",
                  "Not set · Recommended: ${exportLocation.displayPath} · Browser package subfolders are created automatically")
            },
        ) {
          exportDirectoryPicker.launch(BackupManager.defaultDocumentsUri())
        }
        ActionRow(
            Icons.Rounded.Backup,
            tx(chinese, "备份与恢复", "Backup and restore"),
            tx(chinese, "本地 ZIP、WebDAV 与保留策略", "Local ZIP, WebDAV, and retention"),
        ) {
          controller.settingsPage = SettingsPage.Backup
        }
      }
    }
  }
}

@Composable
private fun BrowserManagerPicker(controller: ChromeXtController) {
  val chinese = controller.isChinese
  val connected = controller.connectedBrowserTargets().map { it.packageName }.toSet()
  AlertDialog(
      onDismissRequest = controller::dismissBrowserPicker,
      icon = { Icon(Icons.Rounded.BrowserUpdated, null) },
      title = { Text(tx(chinese, "选择浏览器", "Choose a browser")) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          controller.browserTargets.forEach { target ->
            val online = target.packageName in connected
            Surface(
                modifier =
                    Modifier.fillMaxWidth().clickable {
                      controller.selectBrowserForManager(target)
                    },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
              Row(
                  Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.Rounded.BrowserUpdated, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                  Text(target.label, fontWeight = FontWeight.Medium)
                  Text(
                      "${target.packageName} · ${if (online) tx(chinese, "已连接", "Connected") else tx(chinese, "打开后连接", "Connects after opening")}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Icon(Icons.Rounded.ChevronRight, null)
              }
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = controller::dismissBrowserPicker) {
          Text(tx(chinese, "取消", "Cancel"))
        }
      },
  )
}

@Composable
private fun ScriptsScreen(controller: ChromeXtController) {
  NativeScriptsScreen(controller)
}

@Composable
private fun SettingsScreen(controller: ChromeXtController) {
  controller.revision
  val chinese = controller.isChinese
  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(18.dp, 10.dp, 18.dp, 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      SectionCard(tx(chinese, "界面", "Interface")) {
        ActionRow(
            Icons.Rounded.Palette,
            tx(chinese, "外观", "Appearance"),
            tx(chinese, "主题、颜色与液态玻璃导航栏", "Theme, colors, and liquid glass navigation"),
        ) {
          controller.settingsPage = SettingsPage.Appearance
        }
        ActionRow(
            Icons.Rounded.Language,
            tx(chinese, "语言", "Language"),
            languageLabel(controller.language(), chinese),
        ) {
          controller.settingsPage = SettingsPage.Language
        }
        SwitchRow(
            Icons.Rounded.Apps,
            tx(chinese, "显示桌面图标", "Show launcher icon"),
            if (controller.desktopIconVisible()) {
              tx(chinese, "可从桌面直接打开 ChromeXt", "Open ChromeXt directly from the launcher")
            } else {
              tx(chinese, "已隐藏，请从 LSPosed 模块页打开", "Hidden; open it from the LSPosed module page")
            },
            controller.desktopIconVisible(),
            controller::setDesktopIconVisible,
        )
      }
    }
    item {
      SectionCard(tx(chinese, "数据", "Data")) {
        ActionRow(
            Icons.Rounded.Backup,
            tx(chinese, "备份与恢复", "Backup and restore"),
            tx(chinese, "本地 ZIP、加密与 WebDAV", "Local ZIP, encryption, and WebDAV"),
        ) {
          controller.settingsPage = SettingsPage.Backup
        }
      }
    }
    item {
      SectionCard(tx(chinese, "关于", "About")) {
        ActionRow(
            Icons.Rounded.Info,
            tx(chinese, "关于 ChromeXt", "About ChromeXt"),
            tx(chinese, "版本、更新和项目链接", "Version, updates, and project links"),
        ) {
          controller.settingsPage = SettingsPage.About
        }
      }
    }
  }
}

@Composable
private fun AppearanceScreen(controller: ChromeXtController) {
  controller.revision
  val chinese = controller.isChinese
  val appearance = controller.appearance()
  var modeDialog by remember { mutableStateOf(false) }
  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(18.dp, 10.dp, 18.dp, 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      SectionCard(tx(chinese, "主题", "Theme")) {
        ActionRow(
            Icons.Rounded.Contrast,
            tx(chinese, "主题模式", "Theme mode"),
            themeModeLabel(appearance.themeMode, chinese),
        ) {
          modeDialog = true
        }
        SwitchRow(
            Icons.Rounded.ColorLens,
            tx(chinese, "动态颜色", "Dynamic color"),
            tx(chinese, "Android 12 及以上跟随系统配色", "Use the system palette on Android 12 and later"),
            appearance.dynamicColor,
            controller::setDynamicColor,
        )
        SwitchRow(
            Icons.Rounded.DarkMode,
            tx(chinese, "液态玻璃导航栏", "Liquid glass navigation"),
            tx(chinese, "使用悬浮半透明底部导航栏", "Use a floating translucent bottom bar"),
            appearance.liquidGlass,
            controller::setLiquidGlass,
        )
      }
    }
    item {
      SectionCard(tx(chinese, "主题颜色", "Theme color")) {
        Text(
            tx(chinese, "关闭动态颜色后使用所选配色", "Used when dynamic color is disabled"),
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          ThemeColor.entries.forEach { color ->
            val selected = !appearance.useCustomTheme && appearance.themeColor == color
            Surface(
                modifier = Modifier.size(48.dp).clickable { controller.setThemeColor(color) },
                shape = CircleShape,
                color = Color(color.argb),
                border =
                    if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                    else null,
                shadowElevation = if (selected) 5.dp else 0.dp,
            ) {
              if (selected) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(Icons.Rounded.CheckCircle, null, tint = Color.White)
                }
              }
            }
          }
        }
      }
    }
  }
  if (modeDialog) {
    ChoiceDialog(
        title = tx(chinese, "主题模式", "Theme mode"),
        labels =
            listOf(
                tx(chinese, "跟随系统", "System"),
                tx(chinese, "浅色", "Light"),
                tx(chinese, "深色", "Dark")),
        selected = ThemeMode.entries.indexOf(appearance.themeMode),
        onDismiss = { modeDialog = false },
    ) {
      controller.setThemeMode(ThemeMode.entries[it])
      modeDialog = false
    }
  }
}

@Composable
private fun BackupLandingScreen(controller: ChromeXtController) {
  BackupSettingsScreen(controller)
}

@Composable
private fun AboutScreen(controller: ChromeXtController) {
  val chinese = controller.isChinese
  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(18.dp, 10.dp, 18.dp, 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Card(
          shape = RoundedCornerShape(28.dp),
          colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
              Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                Icon(Icons.Rounded.Code, null, Modifier.padding(18.dp).size(38.dp))
              }
              Text(
                  "ChromeXt",
                  Modifier.padding(top = 14.dp),
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Bold)
              Text(
                  "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                  Modifier.padding(top = 5.dp),
                  color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .74f))
            }
          }
    }
    item {
      UpdateSection(controller) { controller.settingsPage = SettingsPage.ReleaseHistory }
    }
    item {
      SectionCard(tx(chinese, "项目", "Project")) {
        InfoRow(
            Icons.Rounded.BrowserUpdated,
            tx(chinese, "核心能力", "Core capability"),
            tx(chinese, "通过 Xposed 为 Chromium/WebView 提供用户脚本与开发工具", "Userscripts and developer tools for Chromium/WebView through Xposed"),
        )
      }
    }
  }
}

@Composable
private fun LanguageRow(controller: ChromeXtController) {
  val chinese = controller.isChinese
  var dialog by remember { mutableStateOf(false) }
  val detail =
      when (controller.language()) {
        ChromeXtController.LANGUAGE_ZH -> "中文"
        ChromeXtController.LANGUAGE_EN -> "English"
        else -> tx(chinese, "跟随系统", "System")
      }
  ActionRow(Icons.Rounded.Language, tx(chinese, "语言", "Language"), detail) { dialog = true }
  if (dialog) {
    ChoiceDialog(
        tx(chinese, "语言", "Language"),
        listOf(tx(chinese, "跟随系统", "System"), "中文", "English"),
        listOf(
                ChromeXtController.LANGUAGE_SYSTEM,
                ChromeXtController.LANGUAGE_ZH,
                ChromeXtController.LANGUAGE_EN)
            .indexOf(controller.language())
            .coerceAtLeast(0),
        { dialog = false },
    ) { index ->
      controller.setLanguage(
          listOf(
              ChromeXtController.LANGUAGE_SYSTEM,
              ChromeXtController.LANGUAGE_ZH,
              ChromeXtController.LANGUAGE_EN)[index])
      dialog = false
    }
  }
}

@Composable
private fun StatusHero(connected: Boolean, title: String, detail: String) {
  val container =
      if (connected) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.errorContainer
  val content =
      if (connected) MaterialTheme.colorScheme.onPrimaryContainer
      else MaterialTheme.colorScheme.onErrorContainer
  Card(
      Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(28.dp),
      colors = CardDefaults.cardColors(containerColor = container)) {
        Row(
            Modifier.fillMaxWidth().padding(22.dp),
            verticalAlignment = Alignment.CenterVertically) {
              Surface(shape = RoundedCornerShape(18.dp), color = content.copy(alpha = .12f)) {
                Icon(
                    if (connected) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    null,
                    Modifier.padding(13.dp).size(30.dp),
                    tint = content)
              }
              Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = content)
                Text(detail, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = .78f))
              }
            }
      }
}

@Composable
private fun DashboardCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
) {
  val shape = RoundedCornerShape(22.dp)
  Card(
      modifier
          .height(92.dp)
          .clip(shape)
          .clickable(enabled = onClick != null) { onClick?.invoke() },
      shape = shape,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
              Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
              Column(Modifier.padding(start = 12.dp)) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
              }
            }
      }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
  SettingGroup(title, content = content)
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
  NavigationSettingItem(title = title, description = detail, icon = icon, onClick = onClick)
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
  SwitchSettingItem(
      title = title,
      description = detail,
      icon = icon,
      checked = checked,
      onCheckedChange = onChange,
  )
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, detail: String) {
  SettingItem(title = title, description = detail, icon = icon)
}

@Composable
private fun SettingIcon(icon: ImageVector) {
  SettingLeadingIcon(icon)
}

@Composable
private fun BrowserCard(target: BrowserTarget, chinese: Boolean, onClick: () -> Unit) {
  Card(
      Modifier.fillMaxWidth().clickable(onClick = onClick),
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
              SettingIcon(Icons.Rounded.Code)
              Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(target.label, fontWeight = FontWeight.SemiBold)
                Text(target.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Icon(Icons.AutoMirrored.Rounded.OpenInNew, tx(chinese, "打开", "Open"), tint = MaterialTheme.colorScheme.primary)
            }
      }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, detail: String) {
  Card(
      Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(icon, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text(detail, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
      }
}

@Composable
private fun SectionLabel(label: String) {
  Text(label, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ChoiceDialog(
    title: String,
    labels: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(title) },
      text = {
        Column {
          labels.forEachIndexed { index, label ->
            ListItem(
                headlineContent = { Text(label) },
                leadingContent = {
                  if (index == selected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                  else Spacer(Modifier.size(24.dp))
                },
                modifier = Modifier.clickable { onSelect(index) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (index != labels.lastIndex) HorizontalDivider()
          }
        }
      },
      confirmButton = {},
      dismissButton = { TextButton(onClick = onDismiss) { Text("OK") } },
  )
}

private fun themeModeLabel(mode: ThemeMode, chinese: Boolean): String =
    when (mode) {
      ThemeMode.System -> tx(chinese, "跟随系统", "System")
      ThemeMode.Light -> tx(chinese, "浅色", "Light")
      ThemeMode.Dark -> tx(chinese, "深色", "Dark")
    }

private fun tx(chinese: Boolean, zh: String, en: String): String =
    UiLocalization.text(chinese, zh, en)
