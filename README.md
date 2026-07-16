# ChromeXt

ChromeXt adds userscript support and developer tools to Android Chromium and WebView browsers through Xposed. This repository is a personal fork of [JingMatrix/ChromeXt](https://github.com/JingMatrix/ChromeXt) that keeps the existing hook and userscript engine while modernizing the app, browser UI, backup flow, and Xiaomi Browser compatibility.

The original project documentation is preserved in [README.upstream.md](README.upstream.md).

## Highlights

- Material 3 app UI with system/light/dark modes, dynamic color, custom seed colors, and an optional liquid-glass bottom navigation bar.
- Simplified Chinese, Traditional Chinese, and English interfaces, including injected browser menus and browser-hosted pages.
- Native multi-browser script manager with connected-browser switching, URL installation, metadata/rule editing, enable switches, reinstall, and deletion.
- Mobile web script manager with list/detail/source views, URL installation, import/export, batch reinstall, compact actions, and theme synchronization with the app.
- Source editing with line numbers enabled by default and optional syntax highlighting and formatting. Very large scripts are directed to the browser editor to keep the native UI responsive.
- App-themed userscript installation and overwrite confirmation pages, runtime script panel, and a translucent floating launcher.
- Local ZIP backup and restore, optional AES-256 encryption, retention policies, WebDAV backup/restore, and legacy userscript JSON import compatibility.
- GitHub Release checks, Markdown-rendered release notes, update download and signature verification, privacy policy, open-source license, and disclaimer pages.
- LSPosed/libxposed API 102 integration with framework-managed scope and remote preferences.

## Browser connection model

Script databases remain inside their browser processes. Open the target browser before editing its scripts in the native manager, then open or refresh ChromeXt to establish the secure bridge.

ChromeXt supports both direct Binder registration and a ResultReceiver-based reverse handshake. The latter avoids Android package-visibility restrictions seen in current Chrome builds. Browser-side import/export uses the same bridge for Chrome and Xiaomi Browser; configured directories and large files are handled by the app process.

The current implementation has been exercised with:

- Google Chrome (`com.android.chrome`)
- Xiaomi Browser (`com.android.browser`)

Other scoped Chromium/WebView browsers continue to use the upstream compatibility paths.

## Backup and script transfer

Choose a backup directory in ChromeXt before using browser-page import/export. The chosen Storage Access Framework directory is reused for subsequent operations.

- Browser manager export writes `ChromeXt-userscripts-*.json` to the configured directory.
- Browser manager import restores the newest matching userscript export.
- Full app backup writes ZIP archives and can include scripts, script storage, rules, runtime settings, appearance settings, and optionally WebDAV configuration.
- Restore accepts full ZIP backups and legacy userscript JSON backups.

WebDAV test, backup, and restore actions remain disabled until the server, username, and password are complete.

## Local HTTP service

The local HTTP service hosts ChromeXt browser pages on `127.0.0.1`. It is recommended for Chrome and is usually unnecessary for Xiaomi Browser. The app dashboard can toggle it directly; browser processes must be restarted after updating the module APK so they load the new module code.

## Build

Requirements: JDK 21 and the Android SDK used by the Gradle configuration.

Debug build:

```bash
./gradlew app:assembleDebug
```

Release build:

```bash
./gradlew app:assembleRelease
```

Release signing can be provided through environment variables, Gradle properties, or `keystore.properties`:

```properties
CHROMEXT_SIGNING_STORE_FILE=keystore/chromext-release.jks
CHROMEXT_KEYSTORE_PASSWORD=...
CHROMEXT_KEY_ALIAS=chromext
CHROMEXT_KEY_PASSWORD=...
```

The GitHub Actions release workflow expects the equivalent secrets:

- `CHROMEXT_SIGNING_KEYSTORE_BASE64`
- `CHROMEXT_KEYSTORE_PASSWORD`
- `CHROMEXT_KEY_ALIAS`
- `CHROMEXT_KEY_PASSWORD`

The workflow can be dispatched manually with a version tag input. A pushed `v*` tag also builds a signed artifact and creates a draft GitHub Release.

## Validation

Useful local checks:

```bash
./gradlew app:testDebugUnitTest app:lintDebug app:assembleDebug
node --check app/src/main/assets/frontend/app.js
```

## Notes

ChromeXt is experimental software. Only install userscripts and restore backups from sources you trust. WebDAV servers, browsers, script target sites, and GitHub operate under their own terms and privacy policies.

- Current fork: [zhangyxXyz/ChromeXt](https://github.com/zhangyxXyz/ChromeXt)
- Upstream project: [JingMatrix/ChromeXt](https://github.com/JingMatrix/ChromeXt)
- License: [GNU GPL v3](LICENSE)
