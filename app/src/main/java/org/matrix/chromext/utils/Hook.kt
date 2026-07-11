package org.matrix.chromext.utils

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.HookHandle
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

typealias Unhook = HookHandle

typealias Hooker = (param: HookParam) -> Unit

class HookParam internal constructor(private val chain: XposedInterface.Chain) {
  val thisObject: Any?
    get() = chain.thisObject

  val args: Array<Any?> = chain.args.toTypedArray()

  private var resultAssigned = false
  private var throwableAssigned = false
  private var currentResult: Any? = null
  private var currentThrowable: Throwable? = null
  var result: Any?
    get() = currentResult
    set(value) {
      currentResult = value
      currentThrowable = null
      resultAssigned = true
      throwableAssigned = false
    }

  var throwable: Throwable?
    get() = currentThrowable
    set(value) {
      currentThrowable = value
      currentResult = null
      throwableAssigned = true
      resultAssigned = false
    }

  val hasThrowable: Boolean
    get() = currentThrowable != null

  internal fun shouldSkipOriginal(): Boolean = resultAssigned || throwableAssigned

  internal fun setOriginalResult(value: Any?) {
    currentResult = value
    currentThrowable = null
  }

  internal fun setOriginalThrowable(value: Throwable) {
    currentResult = null
    currentThrowable = value
  }
}

private fun Executable.installHook(
    priority: Int,
    before: Hooker? = null,
    after: Hooker? = null,
): HookHandle {
  return ModernXposed.module
      .hook(this)
      .setPriority(priority)
      .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
      .intercept { chain ->
        val param = HookParam(chain)
        runCatching { before?.invoke(param) }.onFailure(Log::ex)
        if (!param.shouldSkipOriginal()) {
          try {
            param.setOriginalResult(chain.proceed(param.args))
          } catch (throwable: Throwable) {
            param.setOriginalThrowable(throwable)
          }
        }
        runCatching { after?.invoke(param) }.onFailure(Log::ex)
        param.throwable?.let { throw it }
        param.result
      }
}

fun Method.hookBefore(
    priority: Int = XposedInterface.PRIORITY_DEFAULT,
    hook: Hooker,
): HookHandle {
  return installHook(priority, before = hook)
}

fun Method.hookAfter(
    priority: Int = XposedInterface.PRIORITY_DEFAULT,
    hooker: Hooker,
): HookHandle {
  return installHook(priority, after = hooker)
}

fun Constructor<*>.hookAfter(
    priority: Int = XposedInterface.PRIORITY_DEFAULT,
    hooker: Hooker,
): HookHandle {
  return installHook(priority, after = hooker)
}

class XposedHookFactory(val priority: Int = XposedInterface.PRIORITY_DEFAULT) {
  private var beforeMethod: Hooker? = null
  private var afterMethod: Hooker? = null

  fun before(before: Hooker) {
    this.beforeMethod = before
  }

  fun after(after: Hooker) {
    this.afterMethod = after
  }

  internal fun install(method: Method): HookHandle =
      method.installHook(priority, beforeMethod, afterMethod)
}

fun Method.hookMethod(
    priority: Int = XposedInterface.PRIORITY_DEFAULT,
    hook: XposedHookFactory.() -> Unit,
): HookHandle {
  val factory = XposedHookFactory(priority)
  hook(factory)
  return factory.install(this)
}
