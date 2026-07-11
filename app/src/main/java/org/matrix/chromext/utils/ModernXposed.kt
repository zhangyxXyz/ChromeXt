package org.matrix.chromext.utils

import android.content.SharedPreferences
import io.github.libxposed.api.XposedModule

object ModernXposed {
  lateinit var module: XposedModule
    internal set

  lateinit var settings: SharedPreferences
    internal set

  val isInitialized: Boolean
    get() = ::module.isInitialized
}
