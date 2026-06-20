# ChromeXt

This repository is a personal fork of [JingMatrix/ChromeXt](https://github.com/JingMatrix/ChromeXt).

ChromeXt adds UserScript support and developer tools to Android Chromium/WebView browsers through Xposed. For the original documentation, see [README.upstream.md](README.upstream.md).

## About This Fork

This fork keeps upstream behavior while improving Xiaomi Browser / MIUI Browser support and mobile script management.

Highlights:

- Xiaomi Browser packages and MIUI / HyperOS WebView hook path.
- More reliable injection on cold start, restore, history navigation, WebView attach, and Activity resume.
- Local script manager at `https://chromext.local/`, including mobile-friendly rule editing, source-on-demand, per-script enable switches, import/export backup, single-script reinstall, batch reinstall, and runtime script menus.
- Runtime floating script panel with per-page userscript commands and quick exclude actions.
- Modern `.user.js` install prompt with reinstall detection and feedback.
- Script exports are written to `Download/ChromeXt/` as `chromext-userscripts-*.json` and can be imported back from the manager.

## Build

Debug build:

```bash
./gradlew assembleDebug
```

Release build:

```bash
./gradlew assembleRelease
```

Release builds are signed when these values are provided through environment variables or `keystore.properties`:

```properties
CHROMEXT_SIGNING_STORE_FILE=keystore/chromext-release.jks
CHROMEXT_KEYSTORE_PASSWORD=...
CHROMEXT_KEY_ALIAS=chromext
CHROMEXT_KEY_PASSWORD=...
```

CI uses the same signing inputs from GitHub Actions secrets.

## Notes

This fork is experimental and tuned around Xiaomi Browser behavior observed on MIUI / HyperOS. Other Chromium/WebView browsers should follow upstream behavior unless specifically touched by compatibility changes.

Upstream project: [JingMatrix/ChromeXt](https://github.com/JingMatrix/ChromeXt)
