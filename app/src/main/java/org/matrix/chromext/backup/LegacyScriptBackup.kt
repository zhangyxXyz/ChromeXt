package org.matrix.chromext.backup

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class LegacyUserScript(val id: String, val source: String)

object LegacyScriptBackup {
  fun parse(raw: String): List<LegacyUserScript> {
    val value = JSONTokener(raw).nextValue()
    val scripts =
        when (value) {
          is JSONObject -> {
            val type = value.optString("type")
            require(type.isBlank() || type == TYPE) { "不是 ChromeXt 旧版脚本备份" }
            require(value.optInt("version", VERSION) <= VERSION) { "不支持的旧版备份版本" }
            value.optJSONArray("scripts") ?: error("旧版备份缺少 scripts")
          }
          is JSONArray -> value
          else -> error("无法识别旧版脚本备份")
        }
    val result =
        (0 until scripts.length()).mapNotNull { index ->
          when (val entry = scripts.opt(index)) {
            is String -> LegacyUserScript("", entry.takeIf(String::isNotBlank) ?: return@mapNotNull null)
            is JSONObject -> {
              val source =
                  entry.optString("source").ifBlank {
                    entry.optString("meta") + entry.optString("code")
                  }
              source.takeIf(String::isNotBlank)?.let {
                LegacyUserScript(entry.optString("id"), it)
              }
            }
            else -> null
          }
        }
    require(result.isNotEmpty()) { "旧版备份中没有可恢复的用户脚本" }
    return result
  }

  const val TYPE = "ChromeXtUserScriptBackup"
  const val VERSION = 1
}
