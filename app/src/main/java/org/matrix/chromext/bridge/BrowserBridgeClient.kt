package org.matrix.chromext.bridge

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Locale
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.ScriptTransferContract
import org.matrix.chromext.UiLocalization
import org.matrix.chromext.utils.Log

object BrowserBridgeClient {
  private val bound = AtomicBoolean(false)
  private val handshakeReceiverRegistered = AtomicBoolean(false)
  private var packageName: String? = null
  private var bridge: IChromeXtBridge? = null
  private var handshakeChannel: ResultReceiver? = null
  private var bindingContext: Context? = null
  private val mainHandler = Handler(Looper.getMainLooper())

  private val browser =
      object : IChromeXtBrowser.Stub() {
        override fun writeSnapshot(destination: ParcelFileDescriptor) {
          Chrome.IO.execute {
            runCatching {
                  ParcelFileDescriptor.AutoCloseOutputStream(destination).bufferedWriter().use {
                    writer -> writer.write(BrowserSnapshot.export().toString())
                  }
                }
                .onFailure(Log::ex)
          }
        }

        override fun restoreSnapshot(
            source: ParcelFileDescriptor,
            resultDestination: ParcelFileDescriptor,
        ) {
          Chrome.IO.execute {
            val result =
                runCatching {
                      val raw =
                          ParcelFileDescriptor.AutoCloseInputStream(source).bufferedReader().use {
                            it.readText()
                          }
                      BrowserSnapshot.restore(raw)
                      JSONObject(mapOf("ok" to true))
                    }
                    .getOrElse { error ->
                      Log.ex(error)
                      JSONObject(
                          mapOf(
                              "ok" to false,
                              "error" to (error.localizedMessage ?: "Restore failed")))
                    }
            runCatching {
                  ParcelFileDescriptor.AutoCloseOutputStream(resultDestination)
                      .bufferedWriter()
                      .use { it.write(result.toString()) }
                }
                .onFailure(Log::ex)
          }
        }

        override fun writeResponse(
            action: String,
            payload: String,
            destination: ParcelFileDescriptor,
        ) {
          Chrome.IO.execute {
            val response =
                runCatching { BrowserScriptApi.request(action, payload) }
                    .getOrElse { error ->
                      Log.ex(error)
                      JSONObject(
                              mapOf(
                                  "error" to
                                      (error.localizedMessage ?: "Operation failed")))
                          .toString()
                    }
            runCatching {
                  ParcelFileDescriptor.AutoCloseOutputStream(destination)
                      .bufferedWriter()
                      .use { it.write(response) }
                }
                .onFailure(Log::ex)
          }
        }

        override fun writeStreamResponse(
            action: String,
            payloadSource: ParcelFileDescriptor,
            destination: ParcelFileDescriptor,
        ) {
          Chrome.IO.execute {
            val response =
                runCatching {
                      val payload =
                          ParcelFileDescriptor.AutoCloseInputStream(payloadSource)
                              .bufferedReader()
                              .use { it.readText() }
                      BrowserScriptApi.request(action, payload)
                    }
                    .getOrElse { error ->
                      Log.ex(error)
                      JSONObject(
                              mapOf(
                                  "error" to
                                      (error.localizedMessage ?: "Operation failed")))
                          .toString()
                    }
            runCatching {
                  ParcelFileDescriptor.AutoCloseOutputStream(destination)
                      .bufferedWriter()
                      .use { it.write(response) }
                }
                .onFailure(Log::ex)
          }
        }
      }

  private val connection =
      object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
          bridge = IChromeXtBridge.Stub.asInterface(service)
          packageName?.let { browserPackage ->
            runCatching { bridge?.registerBrowser(browserPackage, browser) }
                .onSuccess { Log.i("Browser bridge connected for $browserPackage") }
                .onFailure(Log::ex)
          }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
          bridge = null
          bound.set(false)
          scheduleReconnect()
        }

        override fun onBindingDied(name: ComponentName?) {
          bridge = null
          bound.set(false)
          scheduleReconnect()
        }
      }

  private val handshakeReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          if (intent.action != BrowserBridgeHandshake.ACTION_REQUEST) return
          val channel =
              if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(
                    BrowserBridgeHandshake.EXTRA_CHANNEL,
                    ResultReceiver::class.java,
                )
              } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<ResultReceiver>(BrowserBridgeHandshake.EXTRA_CHANNEL)
              } ?: return
          handshakeChannel = channel
          val response =
              Bundle().apply {
                putBinder(BrowserBridgeHandshake.EXTRA_BROWSER_BINDER, browser.asBinder())
              }
          runCatching { channel.send(BrowserBridgeHandshake.RESULT_REGISTER, response) }
              .onSuccess { Log.i("Browser bridge handshake replied for ${context.packageName}") }
              .onFailure(Log::ex)
        }
      }

  fun connect(context: Context, browserPackage: String) {
    packageName = browserPackage
    bindingContext = context.applicationContext ?: context
    registerHandshakeReceiver(bindingContext!!)
    if (!bound.compareAndSet(false, true)) {
      if (bridge != null) runCatching { bridge?.registerBrowser(browserPackage, browser) }
      return
    }
    val intent =
        Intent()
            .setClassName("org.matrix.chromext", "org.matrix.chromext.bridge.BrowserBridgeService")
    val bindingContext = context.applicationContext ?: context
    val didBind =
        runCatching {
              bindingContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
            .onFailure(Log::ex)
            .getOrDefault(false)
    if (!didBind) {
      bound.set(false)
      Log.w("Browser bridge bind was rejected for $browserPackage")
    } else {
      Log.i("Browser bridge bind requested for $browserPackage")
    }
  }

  fun requestTransfer(action: String) {
    val channel = handshakeChannel ?: error(connectionRequiredMessage())
    channel.send(
        BrowserBridgeHandshake.RESULT_TRANSFER,
        Bundle().apply { putString(BrowserBridgeHandshake.EXTRA_TRANSFER_ACTION, action) },
    )
  }

  private fun connectionRequiredMessage(): String {
    val language = Chrome.settings.getString("language", "system") ?: "system"
    UiLocalization.configure(language)
    val chinese =
        language.startsWith("zh", ignoreCase = true) ||
            (language == "system" && Locale.getDefault().language.startsWith("zh"))
    return UiLocalization.text(
        chinese,
        "请先打开 ChromeXt 以建立通信",
        "Open ChromeXt first to establish a connection",
    )
  }

  private fun registerHandshakeReceiver(context: Context) {
    if (!handshakeReceiverRegistered.compareAndSet(false, true)) return
    val filter = IntentFilter(BrowserBridgeHandshake.ACTION_REQUEST)
    runCatching {
          if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                handshakeReceiver,
                filter,
                BrowserBridgeHandshake.PERMISSION,
                null,
                Context.RECEIVER_EXPORTED,
            )
          } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(
                handshakeReceiver,
                filter,
                BrowserBridgeHandshake.PERMISSION,
                null,
            )
          }
        }
        .onFailure {
          handshakeReceiverRegistered.set(false)
          Log.ex(it)
        }
  }

  private fun scheduleReconnect() {
    mainHandler.removeCallbacksAndMessages(RECONNECT_TOKEN)
    mainHandler.postAtTime(
        {
          val context = bindingContext ?: return@postAtTime
          val browserPackage = packageName ?: return@postAtTime
          connect(context, browserPackage)
        },
        RECONNECT_TOKEN,
        android.os.SystemClock.uptimeMillis() + 1_000L,
    )
  }

  private val RECONNECT_TOKEN = Any()
}
