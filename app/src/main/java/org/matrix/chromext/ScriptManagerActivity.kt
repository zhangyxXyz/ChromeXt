package org.matrix.chromext

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.util.Locale
import org.matrix.chromext.utils.Log

class ScriptManagerActivity : Activity() {
  private val pref by lazy { getSharedPreferences("ChromeXt", Context.MODE_PRIVATE) }
  private val blue = Color.rgb(37, 99, 235)
  private val pageBg = Color.rgb(245, 247, 251)
  private val cardBg = Color.WHITE
  private val text = Color.rgb(17, 24, 39)
  private val muted = Color.rgb(102, 112, 133)
  private val line = Color.rgb(222, 226, 233)

  private data class BrowserTarget(val packageName: String, val label: String)

  private fun tint(checked: Int, unchecked: Int): ColorStateList =
      ColorStateList(
          arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
          intArrayOf(checked, unchecked))

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    render()
  }

  private fun langCode(): String {
    val saved = pref.getString("language", "system") ?: "system"
    if (saved == "zh" || saved == "en") return saved
    return if (Locale.getDefault().language.startsWith("zh")) "zh" else "en"
  }

  private fun tr(zh: String, en: String): String = if (langCode() == "zh") zh else en

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

  private fun title(text: String, sizeSp: Float = 16f): TextView =
      TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(this@ScriptManagerActivity.text)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
      }

  private fun body(text: String): TextView =
      TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(muted)
        setPadding(0, dp(4), 0, 0)
      }

  private fun card(): LinearLayout =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background =
            android.graphics.drawable.GradientDrawable().apply {
              setColor(cardBg)
              cornerRadius = dp(12).toFloat()
              setStroke(dp(1), line)
            }
      }

  private fun render() {
    val root =
        ScrollView(this).apply {
          setBackgroundColor(pageBg)
          isFillViewport = true
        }
    val content =
        LinearLayout(this).apply {
          orientation = LinearLayout.VERTICAL
          setPadding(dp(20), dp(20), dp(20), dp(24))
        }
    root.setOnApplyWindowInsetsListener { _, insets ->
      content.setPadding(dp(20), insets.systemWindowInsetTop + dp(14), dp(20), dp(24))
      insets
    }

    content.addView(
        title(tr("脚本与页面设置", "Scripts and Page Settings"), 26f).apply {
          setPadding(0, 0, 0, 0)
        })
    content.addView(
        body(
            tr(
                "管理运行时面板、脚本管理入口和界面语言。",
                "Manage the runtime panel, script manager entry, and interface language.")))

    content.addView(languageCard(), spacedParams())
    content.addView(runtimeCard(), spacedParams())
    content.addView(localServerCard(), spacedParams())
    content.addView(managerCard(), spacedParams())

    root.addView(content)
    setContentView(root)
  }

  private fun languageCard(): View {
    val card = card()
    card.addView(title(tr("语言", "Language")))
    card.addView(body(tr("默认跟随系统语言，也可以固定为中文或英文。", "Follow system language by default, or pin Chinese or English.")))

    val group =
        RadioGroup(this).apply {
          orientation = RadioGroup.VERTICAL
          setPadding(0, dp(10), 0, 0)
        }
    val options =
        listOf(
            "system" to tr("跟随系统", "System"),
            "zh" to "中文",
            "en" to "English",
        )
    options.forEach { (value, label) ->
      group.addView(
          RadioButton(this).apply {
            text = label
            tag = value
            textSize = 15f
            setTextColor(this@ScriptManagerActivity.text)
            buttonTintList = android.content.res.ColorStateList.valueOf(blue)
            id = View.generateViewId()
            isChecked = pref.getString("language", "system") == value
            setPadding(0, dp(4), 0, dp(4))
          })
    }
    group.setOnCheckedChangeListener { rg, checkedId ->
      val selected = rg.findViewById<RadioButton>(checkedId)?.tag as? String ?: return@setOnCheckedChangeListener
      pref.edit().putString("language", selected).commit()
      broadcastSettings(language = selected)
      render()
    }
    card.addView(group)
    return card
  }

  private fun runtimeCard(): View {
    val card = card()
    val row =
        LinearLayout(this).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
        }
    val labels =
        LinearLayout(this).apply {
          orientation = LinearLayout.VERTICAL
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
    labels.addView(title(tr("开启悬浮球", "Enable Floating Button")))
    labels.addView(body(tr("在网页左侧或右侧显示脚本面板入口。", "Show a page-side entry for the script panel.")))
    row.addView(labels)
    row.addView(
        Switch(this).apply {
          isChecked = pref.getBoolean("runtime_launcher_enabled", true)
          thumbTintList = tint(blue, Color.WHITE)
          trackTintList = tint(Color.rgb(191, 219, 254), line)
          setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            pref.edit().putBoolean("runtime_launcher_enabled", checked).commit()
            broadcastSettings(runtimeLauncherEnabled = checked)
          }
        })
    card.addView(row)
    return card
  }

  private fun managerCard(): View {
    val card = card()
    card.addView(title(tr("脚本管理", "Script Manager")))
    card.addView(body(tr("在已支持的浏览器中打开本地脚本管理页面。", "Open the local script manager in a supported browser.")))
    card.addView(
        Button(this).apply {
          text = tr("打开脚本管理", "Open Script Manager")
          textSize = 15f
          setTextColor(Color.WHITE)
          setTypeface(typeface, android.graphics.Typeface.BOLD)
          background =
              android.graphics.drawable.GradientDrawable().apply {
                setColor(blue)
                cornerRadius = dp(8).toFloat()
              }
          setPadding(0, dp(8), 0, dp(8))
          setOnClickListener { openScriptManager() }
        },
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
          topMargin = dp(14)
        })
    return card
  }

  private fun localServerCard(): View {
    val card = card()
    val row =
        LinearLayout(this).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
        }
    val labels =
        LinearLayout(this).apply {
          orientation = LinearLayout.VERTICAL
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
    labels.addView(title(tr("本地 HTTP 服务", "Local HTTP Server")))
    labels.addView(
        body(
            tr(
                "脚本管理界面作为本地 Server 服务。\n目标为 Chrome 浏览器时推荐开启（小米浏览器无需开启）。",
                "Serve the script manager page from a local server.\nRecommended for Chrome; not needed for Mi Browser.")))
    row.addView(labels)
    row.addView(
        Switch(this).apply {
          isChecked = pref.getBoolean(LocalServer.PREF_LOCAL_SERVER_ENABLED, false)
          thumbTintList = tint(blue, Color.WHITE)
          trackTintList = tint(Color.rgb(191, 219, 254), line)
          setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            pref.edit().putBoolean(LocalServer.PREF_LOCAL_SERVER_ENABLED, checked).commit()
            if (!checked) LocalServer.stop()
            broadcastSettings(localServerEnabled = checked)
            render()
          }
        })
    card.addView(row)
    return card
  }

  private fun spacedParams(): LinearLayout.LayoutParams =
      LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(14)
      }

  private fun openScriptManager() {
    val targets = installedSupportedBrowsers()
    when (targets.size) {
      0 -> Log.toast(this, tr("没有找到支持的浏览器", "No supported browser found"))
      1 -> startScriptManagerIntent(scriptManagerIntent(targets.first().packageName))
      else -> openScriptManagerChooser(targets)
    }
  }

  @Suppress("DEPRECATION")
  private fun installedSupportedBrowsers(): List<BrowserTarget> =
      packageManager
          .getInstalledPackages(0)
          .asSequence()
          .filter { it.packageName in supportedPackages }
          .filter { it.applicationInfo?.enabled != false }
          .distinctBy { it.packageName }
          .map { packageInfo ->
            BrowserTarget(packageInfo.packageName, browserLabel(packageInfo))
          }
          .sortedWith(compareBy<BrowserTarget> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName })
          .toList()

  private fun browserLabel(packageInfo: PackageInfo): String {
    val applicationInfo = packageInfo.applicationInfo ?: return packageInfo.packageName
    return runCatching { packageManager.getApplicationLabel(applicationInfo).toString() }
        .getOrDefault(packageInfo.packageName)
  }

  private fun openScriptManagerChooser(targets: List<BrowserTarget>) {
    val intents = targets.map { scriptManagerIntent(it.packageName) }
    val chooser =
        Intent.createChooser(intents.first(), tr("选择浏览器", "Choose Browser"))
            .putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.drop(1).toTypedArray<Parcelable>())
    startScriptManagerIntent(chooser)
  }

  private fun scriptManagerIntent(packageName: String): Intent {
    val host =
        if (pref.getBoolean(LocalServer.PREF_LOCAL_SERVER_ENABLED, false)) {
          "chrome.local"
        } else {
          "chromext.local"
        }
    val managerUrl = "https://${host}/?from=module&ts=${System.currentTimeMillis()}"
    return Intent(Intent.ACTION_VIEW, Uri.parse(managerUrl))
        .setPackage(packageName)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }

  private fun startScriptManagerIntent(intent: Intent) {
    runCatching { startActivity(intent) }
        .onFailure { Log.toast(this, tr("无法打开脚本管理", "Unable to open script manager")) }
  }

  private fun broadcastSettings(
      runtimeLauncherEnabled: Boolean? = null,
      language: String? = null,
      localServerEnabled: Boolean? = null,
  ) {
    fun applyExtras(intent: Intent): Intent {
      runtimeLauncherEnabled?.let { intent.putExtra("runtime_launcher_enabled", it) }
      language?.let { intent.putExtra("language", it) }
      localServerEnabled?.let { intent.putExtra(LocalServer.PREF_LOCAL_SERVER_ENABLED, it) }
      return intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
    }
    supportedPackages
        .filter { runCatching { packageManager.getPackageInfo(it, 0) }.isSuccess }
        .forEach { packageName ->
          sendBroadcast(applyExtras(Intent(ACTION_CHROMEXT_SETTINGS_CHANGED).setPackage(packageName)))
        }
    sendBroadcast(applyExtras(Intent(ACTION_CHROMEXT_SETTINGS_CHANGED)))
  }
}
