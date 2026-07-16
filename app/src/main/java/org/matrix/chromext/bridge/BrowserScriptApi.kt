package org.matrix.chromext.bridge

import android.content.ContentValues
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.script.Script
import org.matrix.chromext.script.ScriptDbHelper
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.script.parseScript

object BrowserScriptApi {
  private var transferStatus: String = ""

  fun request(action: String, payload: String): String =
      when (action) {
        "list" -> listScripts().toString()
        "details" -> readDetails(JSONObject(payload).getString("id")).toString()
        "read" -> readScript(JSONObject(payload).getString("id")).toString()
        "saveMeta" -> saveMetadata(JSONObject(payload)).toString()
        "save" -> saveScript(JSONObject(payload)).toString()
        "toggle" -> toggleScript(JSONObject(payload)).toString()
        "delete" -> deleteScripts(JSONObject(payload)).toString()
        "reinstall" -> reinstallScripts(JSONObject(payload)).toString()
        "installUrl" -> installFromUrl(JSONObject(payload)).toString()
        "exportBundle" -> exportBundle().toString()
        "importBundle" -> importBundle(payload).toString()
        "setTransferStatus" -> setTransferStatus(payload).toString()
        "takeTransferStatus" -> takeTransferStatus().toString()
        else -> error("Unsupported browser bridge action: $action")
      }

  @Synchronized
  private fun setTransferStatus(payload: String): JSONObject {
    transferStatus = payload
    return JSONObject(mapOf("ok" to true))
  }

  @Synchronized
  private fun takeTransferStatus(): JSONObject {
    val pending = transferStatus
    transferStatus = ""
    return JSONObject().apply {
      put("pending", pending.isNotBlank())
      if (pending.isNotBlank()) put("result", JSONObject(pending))
    }
  }

  private fun listScripts(): JSONObject =
      JSONObject().apply {
        put(
            "scripts",
            JSONArray().apply {
              ScriptDbManager.scripts
                  .sortedBy { displayName(it).lowercase() }
                  .forEach { script ->
                    put(
                        JSONObject().apply {
                          put("id", script.id)
                          put("name", displayName(script))
                          put("namespace", metaValue(script.meta, "namespace"))
                          put("version", metaValue(script.meta, "version"))
                          put("disabled", script.disabled)
                          put("matchCount", script.match.size)
                          put("grantCount", script.grant.size)
                          put("installUrl", installUrl(script.meta))
                        })
                  }
            })
      }

  private fun readScript(id: String): JSONObject {
    val script = ScriptDbManager.scripts.find { it.id == id } ?: error("Script not found")
    return JSONObject().apply {
      put("id", script.id)
      put("name", displayName(script))
      put("source", script.meta + script.code)
      put("disabled", script.disabled)
      put("installUrl", installUrl(script.meta))
    }
  }

  private fun readDetails(id: String): JSONObject {
    val script = ScriptDbManager.scripts.find { it.id == id } ?: error("Script not found")
    return JSONObject().apply {
      put("id", script.id)
      put("name", displayName(script))
      put("meta", script.meta)
      put("disabled", script.disabled)
      put("installUrl", installUrl(script.meta))
    }
  }

  private fun saveMetadata(data: JSONObject): JSONObject {
    val id = data.getString("id")
    val old = ScriptDbManager.scripts.find { it.id == id } ?: error("Script not found")
    val updated =
        parseScript(data.getString("meta") + old.code, old.storage?.toString())
            ?: error("Invalid userscript metadata")
    if (updated.id != old.id) removeIds(listOf(old.id))
    ScriptDbManager.insert(updated)
    ScriptDbManager.scripts.removeAll { it.id == old.id || it.id == updated.id }
    ScriptDbManager.scripts.add(updated)
    return JSONObject(mapOf("id" to updated.id, "name" to displayName(updated)))
  }

  private fun saveScript(data: JSONObject): JSONObject {
    val previousId = data.optString("previousId")
    val previous = ScriptDbManager.scripts.find { it.id == previousId }
    val script =
        parseScript(data.getString("source"), previous?.storage?.toString())
            ?: error("Invalid userscript metadata")
    if (previousId.isNotBlank() && previousId != script.id) removeIds(listOf(previousId))
    ScriptDbManager.insert(script)
    ScriptDbManager.scripts.removeAll { it.id == script.id }
    ScriptDbManager.scripts.add(script)
    return JSONObject(mapOf("id" to script.id, "name" to displayName(script)))
  }

  private fun toggleScript(data: JSONObject): JSONObject {
    val old =
        ScriptDbManager.scripts.find { it.id == data.getString("id") }
            ?: error("Script not found")
    val source = setDisabled(old.meta + old.code, data.getBoolean("disabled"))
    val updated = parseScript(source, old.storage?.toString()) ?: error("Invalid userscript metadata")
    ScriptDbManager.insert(updated)
    ScriptDbManager.scripts.remove(old)
    ScriptDbManager.scripts.add(updated)
    return JSONObject(mapOf("id" to updated.id, "disabled" to updated.disabled))
  }

  private fun deleteScripts(data: JSONObject): JSONObject {
    val json = data.getJSONArray("ids")
    val ids = (0 until json.length()).map(json::getString)
    val count = removeIds(ids)
    return JSONObject(mapOf("deleted" to count))
  }

  private fun reinstallScripts(data: JSONObject): JSONObject {
    val json = data.getJSONArray("ids")
    val ids = (0 until json.length()).map(json::getString)
    var updated = 0
    var failed = 0
    ids.forEach { id ->
      val old = ScriptDbManager.scripts.find { it.id == id }
      val url = old?.let { installUrl(it.meta) }.orEmpty()
      if (old == null || url.isBlank()) {
        failed++
      } else {
        runCatching {
              val source = setDisabled(downloadText(url), old.disabled)
              val replacement =
                  parseScript(source, old.storage?.toString()) ?: error("Invalid downloaded script")
              ScriptDbManager.insert(replacement)
              ScriptDbManager.scripts.removeAll { it.id == old.id || it.id == replacement.id }
              ScriptDbManager.scripts.add(replacement)
            }
            .onSuccess { updated++ }
            .onFailure { failed++ }
      }
    }
    return JSONObject(mapOf("updated" to updated, "failed" to failed))
  }

  private fun installFromUrl(data: JSONObject): JSONObject {
    val url = data.getString("url").trim()
    require(url.startsWith("https://") || url.startsWith("http://")) {
      "Only HTTP and HTTPS script URLs are supported"
    }
    val downloaded = downloadText(url)
    val parsed = parseScript(downloaded, null) ?: error("Invalid downloaded script")
    val previous = ScriptDbManager.scripts.find { it.id == parsed.id }
    val source = previous?.let { setDisabled(downloaded, it.disabled) } ?: downloaded
    val script =
        parseScript(source, previous?.storage?.toString()) ?: error("Invalid downloaded script")
    ScriptDbManager.insert(script)
    ScriptDbManager.scripts.removeAll { it.id == script.id }
    ScriptDbManager.scripts.add(script)
    return JSONObject(mapOf("id" to script.id, "name" to displayName(script)))
  }

  private fun exportBundle(): JSONObject =
      JSONObject().apply {
        put("type", "ChromeXtUserScriptBackup")
        put("version", 1)
        put("exportedAt", System.currentTimeMillis())
        put(
            "scripts",
            JSONArray().apply {
              ScriptDbManager.scripts.forEach { script ->
                put(
                    JSONObject().apply {
                      put("id", script.id)
                      put("source", script.meta + script.code)
                    })
              }
            })
      }

  private fun importBundle(raw: String): JSONObject {
    val root = JSONObject(raw)
    val scripts = root.optJSONArray("scripts") ?: error("No scripts found in import file")
    var imported = 0
    var failed = 0
    for (index in 0 until scripts.length()) {
      val source =
          when (val item = scripts.opt(index)) {
            is JSONObject -> item.optString("source")
            is String -> item
            else -> ""
          }
      val parsed = parseScript(source, null)
      if (parsed == null) {
        failed++
        continue
      }
      val previous = ScriptDbManager.scripts.find { it.id == parsed.id }
      val script = parseScript(source, previous?.storage?.toString())
      if (script == null) {
        failed++
        continue
      }
      ScriptDbManager.insert(script)
      ScriptDbManager.scripts.removeAll { it.id == script.id }
      ScriptDbManager.scripts.add(script)
      imported++
    }
    return JSONObject(mapOf("imported" to imported, "failed" to failed))
  }

  private fun removeIds(ids: List<String>): Int {
    if (ids.isEmpty()) return 0
    val helper = ScriptDbHelper(Chrome.getContext())
    val placeholders = ids.joinToString(",") { "?" }
    val deleted = helper.writableDatabase.delete("script", "id IN ($placeholders)", ids.toTypedArray())
    helper.close()
    ScriptDbManager.scripts.removeAll { it.id in ids }
    return deleted
  }

  private fun displayName(script: Script): String =
      metaValue(script.meta, "name").ifBlank { script.id }

  private fun metaValue(meta: String, key: String): String =
      Regex("""(?m)^//\s+@${Regex.escape(key)}\s+(.+)$""")
          .find(meta)
          ?.groups
          ?.get(1)
          ?.value
          ?.trim()
          .orEmpty()

  private fun installUrl(meta: String): String {
    listOf("downloadURL", "installURL", "sourceURL", "url").forEach { key ->
      metaValue(meta, key).takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let {
        return it
      }
    }
    return metaValue(meta, "updateURL").takeIf { it.startsWith("http") }.orEmpty()
  }

  private fun setDisabled(source: String, disabled: Boolean): String {
    val cleaned =
        source
            .replace(Regex("""(?m)^//\s+@(disable|disabled)(\s+.*)?\r?\n?"""), "")
            .replace(Regex("""\n*$"""), "\n")
    if (!disabled) return cleaned
    val end = Regex("""(?m)^// ==/UserScript==\s*$""").find(cleaned)
        ?: return "// @disable\n$cleaned"
    return cleaned.substring(0, end.range.first) +
        "// @disable\n" +
        cleaned.substring(end.range.first)
  }

  private fun downloadText(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 20_000
    connection.instanceFollowRedirects = true
    return connection.inputStream.bufferedReader().use { it.readText() }
  }
}
