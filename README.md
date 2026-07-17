# ChromeXt

ChromeXt uses Xposed to add userscript management and developer tools to Android Chromium and WebView browsers. This fork keeps the upstream script engine while modernizing the app and browser interfaces.

The original documentation is available in [README.upstream.md](README.upstream.md).

## Features

- Material 3 interface with dynamic colors, custom theme colors, multiple languages, and an optional liquid-glass navigation bar.
- Native and browser-based userscript management, including installation, editing, import/export, batch operations, and runtime controls.
- Multi-browser data isolation with local and WebDAV backup/restore, optional AES-256 encryption, and retention policies. Export and backup files are organized into browser package subfolders.
- In-app update checks, release notes, APK download, and signature verification.

## Compatibility

ChromeXt is built for LSPosed/libxposed API 102 and uses framework-managed scope and remote preferences.

Tested browsers:

- Google Chrome (`com.android.chrome`)
- Xiaomi Browser (`com.android.browser`)

Other scoped Chromium/WebView browsers continue to use the upstream compatibility paths.

## Build

JDK 21 and the Android SDK are required.

```bash
./gradlew app:assembleDebug
./gradlew app:assembleRelease
```

Release signing can be configured with environment variables, Gradle properties, or `keystore.properties`. Pushing a `v*` tag runs the signed GitHub Release workflow.

## Project

- Fork: [zhangyxXyz/ChromeXt](https://github.com/zhangyxXyz/ChromeXt)
- Upstream: [JingMatrix/ChromeXt](https://github.com/JingMatrix/ChromeXt)
- License: [GNU GPL v3](LICENSE)
