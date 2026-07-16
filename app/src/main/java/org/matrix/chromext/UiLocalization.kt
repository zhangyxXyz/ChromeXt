package org.matrix.chromext

import android.content.Context
import android.os.Build
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/** Keeps the inline Compose and injected Web UI translations on the same locale. */
object UiLocalization {
  @Volatile private var traditional = false
  private val cache = ConcurrentHashMap<String, String>()

  fun configure(language: String) {
    val locale =
        if (language == "system") Locale.getDefault()
        else Locale.forLanguageTag(language)
    val tag = locale.toLanguageTag().lowercase(Locale.ROOT)
    traditional =
        tag.contains("hant") || tag.startsWith("zh-tw") || tag.startsWith("zh-hk") ||
            tag.startsWith("zh-mo")
  }

  fun text(chinese: Boolean, simplified: String, english: String): String =
      if (!chinese) english else if (traditional) toTraditional(simplified) else simplified

  fun isChinese(context: Context): Boolean {
    val locale = context.resources.configuration.locales[0]
    configure(locale.toLanguageTag())
    return locale.language.equals("zh", ignoreCase = true)
  }

  fun text(context: Context, simplified: String, english: String): String =
      text(isChinese(context), simplified, english)

  fun error(
      chinese: Boolean,
      raw: String?,
      simplifiedFallback: String,
      englishFallback: String,
  ): String {
    val message = raw?.trim().orEmpty()
    if (message.isBlank()) return text(chinese, simplifiedFallback, englishFallback)
    val translated = ERROR_TRANSLATIONS[message]
    if (translated != null) return text(chinese, translated.first, translated.second)
    return if (chinese) {
      if (traditional) toTraditional(message) else message
    } else {
      message
    }
  }

  fun toTraditional(value: String): String {
    if (value.isBlank()) return value
    return cache.getOrPut(value) {
      if (Build.VERSION.SDK_INT < 29) value
      else {
        runCatching {
              val type = Class.forName("android.icu.text.Transliterator")
              val instance =
                  type.getMethod("getInstance", String::class.java)
                      .invoke(null, "Simplified-Traditional")
              type.getMethod("transliterate", String::class.java).invoke(instance, value) as String
            }
            .getOrDefault(value)
      }
    }
  }

  fun traditionalJson(source: JSONObject): JSONObject {
    val result = JSONObject()
    source.keys().forEach { key -> result.put(key, traditionalValue(source.get(key))) }
    return result
  }

  private fun traditionalValue(value: Any?): Any? =
      when (value) {
        is String -> toTraditional(value)
        is JSONObject -> traditionalJson(value)
        is JSONArray ->
            JSONArray().also { result ->
              for (index in 0 until value.length()) result.put(traditionalValue(value.get(index)))
            }
        else -> value
      }

  private val ERROR_TRANSLATIONS =
      mapOf(
          "No connected browser" to ("浏览器尚未连接" to "No browser is connected"),
          "Script not found" to ("未找到脚本" to "Script not found"),
          "Invalid userscript metadata" to ("用户脚本元数据无效" to "Invalid userscript metadata"),
          "目标浏览器尚未连接" to ("目标浏览器尚未连接" to "The target browser is not connected"),
          "浏览器返回了空快照" to ("浏览器返回了空快照" to "The browser returned an empty snapshot"),
          "备份快照不存在" to ("备份快照不存在" to "The backup snapshot does not exist"),
          "浏览器未确认恢复完成" to ("浏览器未确认恢复完成" to "The browser did not confirm the restore"),
          "请先选择本地备份目录" to ("请先选择本地备份目录" to "Choose a local backup folder first"),
          "本地备份目录不可用，请重新选择" to ("本地备份目录不可用，请重新选择" to "The local backup folder is unavailable; choose it again"),
          "本地备份文件不可用" to ("本地备份文件不可用" to "The local backup file is unavailable"),
          "本地备份目录不可用" to ("本地备份目录不可用" to "The local backup folder is unavailable"),
          "无法在所选目录创建备份" to ("无法在所选目录创建备份" to "Could not create a backup in the selected folder"),
          "无法写入本地备份" to ("无法写入本地备份" to "Could not write the local backup"),
          "无法读取备份文件" to ("无法读取备份文件" to "Could not read the backup file"),
          "WebDAV 配置不完整" to ("WebDAV 配置不完整" to "WebDAV configuration is incomplete"),
          "WebDAV 备份失败" to ("WebDAV 备份失败" to "WebDAV backup failed"),
          "请先配置 WebDAV" to ("请先配置 WebDAV" to "Configure WebDAV first"),
          "备份缺少 manifest.json" to ("备份缺少 manifest.json" to "The backup is missing manifest.json"),
          "不是 ChromeXt 完整备份" to ("不是 ChromeXt 完整备份" to "This is not a complete ChromeXt backup"),
          "不支持的备份版本" to ("不支持的备份版本" to "Unsupported backup version"),
          "备份缺少浏览器数据" to ("备份缺少浏览器数据" to "The backup is missing browser data"),
          "不是 ChromeXt 旧版脚本备份" to ("不是 ChromeXt 旧版脚本备份" to "This is not a legacy ChromeXt script backup"),
          "不支持的旧版备份版本" to ("不支持的旧版备份版本" to "Unsupported legacy backup version"),
          "旧版备份缺少 scripts" to ("旧版备份缺少 scripts" to "The legacy backup is missing scripts"),
          "无法识别旧版脚本备份" to ("无法识别旧版脚本备份" to "Unrecognized legacy script backup"),
          "旧版备份中没有可恢复的用户脚本" to ("旧版备份中没有可恢复的用户脚本" to "The legacy backup contains no restorable userscripts"),
          "无法读取更新 APK" to ("无法读取更新 APK" to "Could not read the update APK"),
          "更新 APK 包名不匹配" to ("更新 APK 包名不匹配" to "The update APK package name does not match"),
          "更新 APK 签名不匹配" to ("更新 APK 签名不匹配" to "The update APK signature does not match"),
          "脚本数据操作失败" to ("脚本数据操作失败" to "Script data operation failed"),
          "脚本数据目录不可用，请重新设置" to
              ("脚本数据目录不可用，请重新设置" to
                  "The script data folder is unavailable; choose it again"),
          "脚本数据文件不存在" to ("脚本数据文件不存在" to "The script data file does not exist"),
          "浏览器返回的脚本数据无效" to
              ("浏览器返回的脚本数据无效" to "The browser returned invalid script data"),
          "目录中没有脚本导出文件" to
              ("目录中没有脚本导出文件" to "No script export was found in the folder"),
          "请先打开 ChromeXt 以建立通信" to
              ("请先打开 ChromeXt 以建立通信" to "Open ChromeXt first to establish a connection"),
          "请先设置脚本数据目录" to
              ("请先设置脚本数据目录" to "Choose a script data folder first"),
          "未选择脚本数据目录" to
              ("未选择脚本数据目录" to "No script data folder was selected"),
          "无法读取脚本导入文件" to
              ("无法读取脚本导入文件" to "Could not read the script import file"),
          "无法写入导出文件" to
              ("无法写入导出文件" to "Could not write the script export"),
          "无法在所选目录创建导出文件" to
              ("无法在所选目录创建导出文件" to
                  "Could not create a script export in the selected folder"),
          "无效的脚本数据操作" to ("无效的脚本数据操作" to "Invalid script data operation"),
      )
}
