package org.matrix.chromext

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

class SettingsProvider : ContentProvider() {
  override fun onCreate(): Boolean = true

  override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
    if (method != "settings") return null
    val pref = context?.getSharedPreferences("ChromeXt", Context.MODE_PRIVATE) ?: return null
    return Bundle().apply {
      putBoolean("runtime_launcher_enabled", pref.getBoolean("runtime_launcher_enabled", true))
      putString("language", pref.getString("language", "system") ?: "system")
      putBoolean(
          LocalServer.PREF_LOCAL_SERVER_ENABLED,
          pref.getBoolean(LocalServer.PREF_LOCAL_SERVER_ENABLED, false))
    }
  }

  override fun query(
      uri: Uri,
      projection: Array<out String>?,
      selection: String?,
      selectionArgs: Array<out String>?,
      sortOrder: String?
  ): Cursor? = null

  override fun getType(uri: Uri): String? = null

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

  override fun update(
      uri: Uri,
      values: ContentValues?,
      selection: String?,
      selectionArgs: Array<out String>?
  ): Int = 0
}
