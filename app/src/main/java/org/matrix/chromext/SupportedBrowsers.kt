package org.matrix.chromext

val miBrowserPackages = setOf("com.android.browser", "com.mi.globalbrowser")

val supportedPackages: Set<String> by lazy {
  checkNotNull(
          SupportedBrowserResource::class.java.classLoader?.getResourceAsStream(
              "META-INF/xposed/scope.list"
          )
      ) {
        "Missing META-INF/xposed/scope.list"
      }
      .bufferedReader()
      .useLines { lines ->
        lines.map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }.toSet()
      }
}

val chromiumPackages: Set<String> by lazy { supportedPackages - miBrowserPackages }

private object SupportedBrowserResource
