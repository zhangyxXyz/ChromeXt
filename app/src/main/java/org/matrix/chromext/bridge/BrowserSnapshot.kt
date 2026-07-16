package org.matrix.chromext.bridge

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.script.ScriptDbHelper
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.script.parseScript

object BrowserSnapshot {
  private const val TYPE = "ChromeXtBrowserSnapshot"
  private const val VERSION = 1
  private val preferenceNames = listOf("ChromeXt", "CosmeticFilter", "UserAgent", "CSPRule")

  fun export(): JSONObject {
    val context = Chrome.getContext()
    val scripts =
        JSONArray().apply {
          ScriptDbManager.scripts.forEach { script ->
            put(
                JSONObject().apply {
                  put("id", script.id)
                  put("meta", script.meta)
                  put("code", script.code)
                  put("storage", script.storage ?: JSONObject.NULL)
                })
          }
        }
    val preferences =
        JSONObject().apply {
          preferenceNames.forEach { name ->
            put(name, encodePreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE)))
          }
        }
    return JSONObject().apply {
      put("type", TYPE)
      put("version", VERSION)
      put("browserPackage", context.packageName)
      put("exportedAt", System.currentTimeMillis())
      put("scripts", scripts)
      put("preferences", preferences)
    }
  }

  fun restore(raw: String) {
    val root = JSONObject(raw)
    require(root.optString("type") == TYPE) { "Unsupported ChromeXt browser snapshot" }
    require(root.optInt("version") == VERSION) { "Unsupported snapshot version" }
    val context = Chrome.getContext()
    val parsedScripts =
        buildList {
          val scripts = root.getJSONArray("scripts")
          for (index in 0 until scripts.length()) {
            val item = scripts.getJSONObject(index)
            val storage =
                if (item.isNull("storage")) null else item.getJSONObject("storage").toString()
            parseScript(item.getString("meta") + item.getString("code"), storage)?.let(::add)
                ?: error("Invalid script at index $index")
          }
        }

    val helper = ScriptDbHelper(context)
    helper.writableDatabase.delete("script", null, null)
    helper.close()
    ScriptDbManager.scripts.clear()
    parsedScripts.forEach { script ->
      ScriptDbManager.insert(script)
      ScriptDbManager.scripts.add(script)
    }

    root.optJSONObject("preferences")?.let { preferences ->
      preferenceNames.forEach { name ->
        preferences.optJSONObject(name)?.let {
          restorePreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE), it)
        }
      }
    }
    refreshPreferenceCaches(context)
  }

  private fun encodePreferences(preferences: SharedPreferences): JSONObject =
      JSONObject().apply {
        preferences.all.forEach { (key, value) ->
          when (value) {
            is Set<*> -> put(key, JSONArray(value.filterIsInstance<String>()))
            else -> put(key, value)
          }
        }
      }

  private fun restorePreferences(preferences: SharedPreferences, values: JSONObject) {
    val editor = preferences.edit().clear()
    values.keys().forEach { key ->
      when (val value = values.get(key)) {
        is Boolean -> editor.putBoolean(key, value)
        is Int -> editor.putInt(key, value)
        is Long -> editor.putLong(key, value)
        is Double -> editor.putFloat(key, value.toFloat())
        is String -> editor.putString(key, value)
        is JSONArray ->
            editor.putStringSet(
                key, (0 until value.length()).map(value::getString).toSet())
      }
    }
    editor.commit()
  }

  @Suppress("UNCHECKED_CAST")
  private fun refreshPreferenceCaches(context: Context) {
    fun stringMap(name: String): Map<String, String> =
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all as Map<String, String>
    ScriptDbManager.cosmeticFilters.clear()
    ScriptDbManager.cosmeticFilters.putAll(stringMap("CosmeticFilter"))
    ScriptDbManager.userAgents.clear()
    ScriptDbManager.userAgents.putAll(stringMap("UserAgent"))
    ScriptDbManager.cspRules.clear()
    ScriptDbManager.cspRules.putAll(stringMap("CSPRule"))
    ScriptDbManager.keepStorage =
        context
            .getSharedPreferences("ChromeXt", Context.MODE_PRIVATE)
            .getBoolean("keep_storage", true)
  }
}
