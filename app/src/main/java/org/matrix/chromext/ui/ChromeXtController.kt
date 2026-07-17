package org.matrix.chromext.ui

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.os.Parcelable
import android.app.LocaleManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import org.matrix.chromext.LocalServer
import org.matrix.chromext.ScriptManagerActivity
import org.matrix.chromext.XposedServiceRepository
import org.matrix.chromext.UiLocalization
import org.matrix.chromext.bridge.BrowserBridgeService
import org.matrix.chromext.bridge.BrowserBridgeHandshake
import org.matrix.chromext.supportedPackages
import org.matrix.chromext.utils.Log

data class BrowserTarget(val packageName: String, val label: String)

class ChromeXtController(private val activity: ScriptManagerActivity) {
  private val legacyPref = activity.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
  private val uiPref = activity.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
  private var remotePref: SharedPreferences? = null
  private var remotePrefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
  private var localServerOverride by mutableStateOf<Boolean?>(null)
  private var runtimeLauncherOverride by mutableStateOf<Boolean?>(null)
  private var desktopIconVisibleState by mutableStateOf(readDesktopIconVisible())
  private val serviceListener = XposedServiceRepository.Listener { bindRemoteSettings() }
  private val bridgeListener: () -> Unit = { refresh() }
  private val lastHandshakeAt = mutableMapOf<String, Long>()

  val context: Context
    get() = activity

  var revision by mutableIntStateOf(0)
    private set

  var currentTab by mutableStateOf(MainTab.Home)
  var settingsPage by mutableStateOf(SettingsPage.Root)
  var browserPickerVisible by mutableStateOf(false)
    private set
  var browserTargets by mutableStateOf<List<BrowserTarget>>(emptyList())
    private set

  val runtimePref: SharedPreferences
    get() = remotePref ?: legacyPref

  val isConnected: Boolean
    get() = XposedServiceRepository.isConnected

  val connectionError: String?
    get() = XposedServiceRepository.error?.localizedMessage

  val isChinese: Boolean
    get() {
      val language = runtimePref.getString(KEY_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
      UiLocalization.configure(language)
      return language.startsWith("zh", ignoreCase = true) ||
          (language == LANGUAGE_SYSTEM && Locale.getDefault().language.startsWith("zh"))
    }

  fun start() {
    XposedServiceRepository.initialize(activity)
    XposedServiceRepository.addListener(serviceListener)
    BrowserBridgeService.Registry.addListener(bridgeListener)
    refresh()
  }

  fun stop() {
    remotePrefListener?.let { remotePref?.unregisterOnSharedPreferenceChangeListener(it) }
    XposedServiceRepository.removeListener(serviceListener)
    BrowserBridgeService.Registry.removeListener(bridgeListener)
  }

  fun refresh() {
    desktopIconVisibleState = readDesktopIconVisible()
    browserTargets = scopedSupportedBrowsers()
    val now = System.currentTimeMillis()
    browserTargets
        .filterNot { BrowserBridgeHandshake.hasChannel(it.packageName) }
        .forEach { target ->
          val last = lastHandshakeAt[target.packageName] ?: 0L
          if (now - last >= 2_000L) {
            lastHandshakeAt[target.packageName] = now
            BrowserBridgeHandshake.request(activity, target.packageName)
          }
        }
    revision++
  }

  private fun bindRemoteSettings() {
    val preferences = XposedServiceRepository.settings
    if (remotePref !== preferences) {
      remotePrefListener?.let { remotePref?.unregisterOnSharedPreferenceChangeListener(it) }
      remotePref = preferences
      remotePrefListener =
          preferences?.let { remote ->
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                  if (key == LocalServer.PREF_LOCAL_SERVER_ENABLED) localServerOverride = null
                  if (key == KEY_RUNTIME_LAUNCHER) runtimeLauncherOverride = null
                  refresh()
                }
                .also(remote::registerOnSharedPreferenceChangeListener)
          }
    }
    if (remotePref != null) syncAppearance()
    refresh()
  }

  @Composable
  fun ObserveAppearance(content: @Composable (AppearanceState) -> Unit) {
    revision
    content(appearance())
  }

  fun appearance(): AppearanceState {
    val mode =
        runCatching {
              ThemeMode.valueOf(uiPref.getString(KEY_THEME_MODE, ThemeMode.System.name).orEmpty())
            }
            .getOrDefault(ThemeMode.System)
    val color =
        runCatching {
              ThemeColor.valueOf(uiPref.getString(KEY_THEME_COLOR, ThemeColor.Purple.name).orEmpty())
            }
            .getOrDefault(ThemeColor.Purple)
    return AppearanceState(
        themeMode = mode,
        dynamicColor = uiPref.getBoolean(KEY_DYNAMIC_COLOR, true),
        themeColor = color,
        customThemeColor = uiPref.getString(KEY_CUSTOM_COLOR, "#6750A4") ?: "#6750A4",
        useCustomTheme = uiPref.getBoolean(KEY_USE_CUSTOM_COLOR, false),
        liquidGlass = uiPref.getBoolean(KEY_LIQUID_GLASS, false),
    )
  }

  fun setThemeMode(value: ThemeMode) {
    updateUi { putString(KEY_THEME_MODE, value.name) }
    mirrorAppearance { putString("ui_theme_mode", value.name) }
  }

  fun setDynamicColor(value: Boolean) {
    updateUi { putBoolean(KEY_DYNAMIC_COLOR, value) }
    mirrorAppearance { putBoolean("ui_dynamic_color", value) }
  }

  fun setThemeColor(value: ThemeColor) {
    updateUi {
        putString(KEY_THEME_COLOR, value.name)
        putBoolean(KEY_USE_CUSTOM_COLOR, false)
        putBoolean(KEY_DYNAMIC_COLOR, false)
      }
    mirrorAppearance {
      putBoolean("ui_dynamic_color", false)
      putString("ui_theme_seed", "#%06X".format(value.argb and 0xFFFFFF))
    }
  }

  fun setCustomThemeColor(value: String) {
    val normalized = value.uppercase(Locale.ROOT)
    updateUi {
      putString(KEY_CUSTOM_COLOR, normalized)
      putBoolean(KEY_USE_CUSTOM_COLOR, true)
      putBoolean(KEY_DYNAMIC_COLOR, false)
    }
    mirrorAppearance {
      putBoolean("ui_dynamic_color", false)
      putString("ui_theme_seed", normalized)
    }
  }

  fun setLiquidGlass(value: Boolean) {
    updateUi { putBoolean(KEY_LIQUID_GLASS, value) }
    mirrorAppearance { putBoolean("ui_liquid_glass", value) }
  }

  fun desktopIconVisible(): Boolean = desktopIconVisibleState

  private fun readDesktopIconVisible(): Boolean =
      when (activity.packageManager.getComponentEnabledSetting(launcherComponent())) {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
        else -> true
      }

  fun setDesktopIconVisible(value: Boolean) {
    runCatching {
          activity.packageManager.setComponentEnabledSetting(
              launcherComponent(),
              if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
              else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
              PackageManager.DONT_KILL_APP,
          )
          desktopIconVisibleState = value
          revision++
        }
        .onFailure {
          Log.toast(
              activity,
              if (isChinese) "桌面图标设置失败" else "Could not update the launcher icon",
          )
        }
  }

  private fun launcherComponent(): ComponentName =
      ComponentName(activity.packageName, "${activity.packageName}.LauncherAlias")

  fun setResolvedThemeDark(value: Boolean) {
    val target = remotePref ?: return
    if (target.getBoolean(KEY_RESOLVED_THEME_DARK, !value) == value) return
    target.edit().putBoolean(KEY_RESOLVED_THEME_DARK, value).apply()
  }

  private fun mirrorAppearance(block: SharedPreferences.Editor.() -> Unit) {
    val target = remotePref ?: return
    if (target.edit().apply(block).commit()) {
      BrowserBridgeService.Registry.notifySettingsChanged()
    }
  }

  private fun syncAppearance() {
    val current = appearance()
    mirrorAppearance {
      putString("ui_theme_mode", current.themeMode.name)
      putBoolean("ui_dynamic_color", current.dynamicColor)
      putString(
          "ui_theme_seed",
          if (current.useCustomTheme) current.customThemeColor
          else "#%06X".format(current.themeColor.argb and 0xFFFFFF))
      putBoolean("ui_liquid_glass", current.liquidGlass)
    }
  }

  private fun updateUi(block: SharedPreferences.Editor.() -> Unit) {
    uiPref.edit().apply(block).apply()
    revision++
  }

  fun language(): String =
      runtimePref.getString(KEY_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM

  fun setLanguage(value: String) {
    editRuntime { putString(KEY_LANGUAGE, value) }
    val languageTags = if (value == LANGUAGE_SYSTEM) "" else value
    if (Build.VERSION.SDK_INT >= 33) {
      activity.getSystemService(LocaleManager::class.java).applicationLocales =
          LocaleList.forLanguageTags(languageTags)
    } else {
      AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTags))
    }
  }

  fun runtimeLauncherEnabled(): Boolean =
      runtimeLauncherOverride ?: runtimePref.getBoolean(KEY_RUNTIME_LAUNCHER, true)

  fun setRuntimeLauncherEnabled(value: Boolean) {
    runtimeLauncherOverride = value
    revision++
    if (!editRuntime { putBoolean(KEY_RUNTIME_LAUNCHER, value) }) runtimeLauncherOverride = null
  }

  fun localServerEnabled(): Boolean =
      localServerOverride
          ?: runtimePref.getBoolean(LocalServer.PREF_LOCAL_SERVER_ENABLED, false)

  fun setLocalServerEnabled(value: Boolean) {
    localServerOverride = value
    revision++
    if (!editRuntime { putBoolean(LocalServer.PREF_LOCAL_SERVER_ENABLED, value) }) {
      localServerOverride = null
    }
    if (!value) LocalServer.stop()
  }

  private fun editRuntime(block: SharedPreferences.Editor.() -> Unit): Boolean {
    val target = remotePref
    if (target == null) {
      Log.toast(
          activity,
          if (isChinese) "请先在 LSPosed 中启用模块并重新打开" else "Enable the module in LSPosed first")
      return false
    }
    return runCatching {
          check(target.edit().apply(block).commit()) { "Remote settings commit failed" }
          BrowserBridgeService.Registry.notifySettingsChanged()
          revision++
          true
        }
        .getOrElse {
          Log.toast(activity, if (isChinese) "设置保存失败" else "Could not save setting")
          false
        }
  }

  fun retryConnection() = XposedServiceRepository.retry()

  fun connectedBrowserTargets(): List<BrowserTarget> {
    val connected = BrowserBridgeService.Registry.connectedPackages()
    return browserTargets.filter { it.packageName in connected }
  }

  fun openScriptManager(target: BrowserTarget? = null, sourceScriptId: String? = null) {
    val targets = browserTargets
    when {
      target != null ->
          startScriptManagerIntent(scriptManagerIntent(target.packageName, sourceScriptId))
      targets.isEmpty() ->
          Log.toast(
              activity,
              if (isChinese) "没有找到已安装且处于作用域内的支持浏览器"
              else "No installed supported browser is enabled in the module scope")
      targets.size == 1 ->
          startScriptManagerIntent(scriptManagerIntent(targets.first().packageName, sourceScriptId))
      else -> browserPickerVisible = true
    }
  }

  fun dismissBrowserPicker() {
    browserPickerVisible = false
  }

  fun selectBrowserForManager(target: BrowserTarget) {
    browserPickerVisible = false
    startScriptManagerIntent(scriptManagerIntent(target.packageName))
  }

  private fun scriptManagerIntent(packageName: String, sourceScriptId: String? = null): Intent {
    val host = if (localServerEnabled()) "chrome.local" else "chromext.local"
    val uri =
        Uri.Builder()
            .scheme("https")
            .authority(host)
            .appendQueryParameter("from", "module")
            .appendQueryParameter("ts", System.currentTimeMillis().toString())
            .apply {
              sourceScriptId?.takeIf(String::isNotBlank)?.let { fragment("source=$it") }
            }
            .build()
    return Intent(
            Intent.ACTION_VIEW,
            uri)
        .setPackage(packageName)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }

  private fun startScriptManagerIntent(intent: Intent) {
    runCatching { activity.startActivity(intent) }
        .onFailure {
          Log.toast(activity, if (isChinese) "无法打开脚本管理" else "Unable to open script manager")
        }
  }

  @Suppress("DEPRECATION")
  private fun scopedSupportedBrowsers(): List<BrowserTarget> =
      runCatching { XposedServiceRepository.getScope() }
          .getOrElse { emptyList() }
          .asSequence()
          .filter { it in supportedPackages }
          .distinct()
          .mapNotNull { packageName ->
            val packageInfo =
                runCatching { activity.packageManager.getPackageInfo(packageName, 0) }.getOrNull()
                    ?: return@mapNotNull null
            if (packageInfo.applicationInfo?.enabled == false) return@mapNotNull null
            BrowserTarget(packageInfo.packageName, browserLabel(packageInfo))
          }
          .sortedWith(
              compareBy<BrowserTarget> { it.label.lowercase(Locale.ROOT) }
                  .thenBy { it.packageName })
          .toList()

  private fun browserLabel(packageInfo: PackageInfo): String {
    val info = packageInfo.applicationInfo ?: return packageInfo.packageName
    return runCatching { activity.packageManager.getApplicationLabel(info).toString() }
        .getOrDefault(packageInfo.packageName)
  }

  companion object {
    private const val RUNTIME_PREFS = "ChromeXt"
    private const val UI_PREFS = "ChromeXtUi"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_RUNTIME_LAUNCHER = "runtime_launcher_enabled"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_THEME_COLOR = "theme_color"
    private const val KEY_CUSTOM_COLOR = "custom_theme_color"
    private const val KEY_USE_CUSTOM_COLOR = "use_custom_theme"
    private const val KEY_LIQUID_GLASS = "liquid_glass"
    private const val KEY_RESOLVED_THEME_DARK = "ui_theme_dark"
    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_ZH = "zh-CN"
    const val LANGUAGE_ZH_TW = "zh-TW"
    const val LANGUAGE_EN = "en"
  }
}
