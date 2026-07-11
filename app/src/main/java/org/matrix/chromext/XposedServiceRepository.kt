package org.matrix.chromext

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

object XposedServiceRepository {
  fun interface Listener {
    fun onStateChanged()
  }

  private const val SETTINGS_GROUP = "ChromeXt"
  private const val MIGRATION_MARKER = "_local_settings_migrated_v1"
  private val initialized = AtomicBoolean(false)
  private val listeners = CopyOnWriteArraySet<Listener>()
  private val mainHandler = Handler(Looper.getMainLooper())
  private lateinit var appContext: Context

  @Volatile private var service: XposedService? = null
  @Volatile var settings: SharedPreferences? = null
    private set
  @Volatile var error: Throwable? = null
    private set

  val isConnected: Boolean
    get() = service != null && settings != null

  val isServiceAvailable: Boolean
    get() = service != null

  fun initialize(context: Context) {
    appContext = context.applicationContext
    if (!initialized.compareAndSet(false, true)) return
    XposedServiceHelper.registerListener(
        object : XposedServiceHelper.OnServiceListener {
          override fun onServiceBind(boundService: XposedService) {
            connect(boundService)
          }

          override fun onServiceDied(deadService: XposedService) {
            if (service === deadService) {
              service = null
              settings = null
              error = IllegalStateException("Xposed service disconnected")
              notifyListeners()
            }
          }
        })
  }

  fun addListener(listener: Listener) {
    listeners.add(listener)
    mainHandler.post(listener::onStateChanged)
  }

  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }

  fun retry() {
    service?.let(::connect) ?: notifyListeners()
  }

  fun getScope(): List<String> {
    return runCatching { service?.scope.orEmpty() }
        .onFailure {
          error = it
          notifyListeners()
        }
        .getOrDefault(emptyList())
  }

  private fun connect(boundService: XposedService) {
    runCatching {
          check(boundService.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L) {
            "The Xposed framework does not support remote preferences"
          }
          val remoteSettings = boundService.getRemotePreferences(SETTINGS_GROUP)
          migrateLegacySettings(remoteSettings)
          service = boundService
          settings = remoteSettings
          error = null
        }
        .onFailure {
          service = boundService
          settings = null
          error = it
        }
    notifyListeners()
  }

  private fun migrateLegacySettings(remoteSettings: SharedPreferences) {
    if (remoteSettings.getBoolean(MIGRATION_MARKER, false)) return
    val legacy = appContext.getSharedPreferences(SETTINGS_GROUP, Context.MODE_PRIVATE)
    val editor = remoteSettings.edit()
    listOf("runtime_launcher_enabled", "language", LocalServer.PREF_LOCAL_SERVER_ENABLED)
        .forEach { key ->
          if (!legacy.contains(key)) return@forEach
          when (val value = legacy.all[key]) {
            is Boolean -> editor.putBoolean(key, value)
            is String -> editor.putString(key, value)
          }
        }
    editor.putBoolean(MIGRATION_MARKER, true).apply()
  }

  private fun notifyListeners() {
    mainHandler.post { listeners.forEach(Listener::onStateChanged) }
  }
}
