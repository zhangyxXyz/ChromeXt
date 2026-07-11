package org.matrix.chromext.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import java.lang.ref.WeakReference
import org.matrix.chromext.BuildConfig
import org.matrix.chromext.TAG

object Log {
  private var lastToast: WeakReference<Toast>? = null

  fun i(msg: String) {
    Log.i(TAG, msg)
    if (ModernXposed.isInitialized) {
      ModernXposed.module.log(android.util.Log.INFO, TAG, msg)
    }
  }

  fun d(msg: String, full: Boolean = false) {
    if (BuildConfig.DEBUG) {
      if (!full && msg.length > 300) {
        Log.d(TAG, msg.take(300) + " ...")
      } else {
        Log.d(TAG, msg)
      }
    }
  }

  fun w(msg: String) {
    Log.w(TAG, msg)
  }

  fun e(msg: String) {
    Log.e(TAG, msg)
    if (ModernXposed.isInitialized) {
      ModernXposed.module.log(android.util.Log.ERROR, TAG, msg)
    }
  }

  fun ex(thr: Throwable, msg: String = "") {
    Log.e(TAG, msg, thr)
    if (ModernXposed.isInitialized) {
      ModernXposed.module.log(android.util.Log.ERROR, TAG, msg, thr)
    }
  }

  fun toast(context: Context, msg: String) {
    this.lastToast?.get()?.cancel()
    val duration = Toast.LENGTH_SHORT
    val toast = Toast.makeText(context, msg, duration)
    toast.show()
    this.lastToast = WeakReference(toast)
  }
}
