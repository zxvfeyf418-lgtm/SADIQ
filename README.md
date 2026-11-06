<p align="center"><img src="docs/assets/icon.png" width="96" alt=""></p>

<h1 align="center">2n5a</h1>

<p align="center">مشاركة إنترنت (Wi-Fi/USB) عبر Shizuku + إنشاء ملفات WARP — تطبيق أندرويد (Kotlin, Compose).</p>

2n5a is [Shizzi](https://github.com/carlelieser/shizzi) v0.5.0 (hotspot sharing through a
Shizuku-privileged test network) with a fully Arabic RTL interface, an animated welcome screen
and a built-in Cloudflare WARP config generator.
**Application ID and package are unchanged (`dev.shizzi`)**.

## What it does

- **مشاركة الإنترنت:** hotspot/USB sharing over Shizuku. Modes: follow any active VPN / refuse
  sharing without a VPN / ignore the VPN.
- **إنشاء ملفات:** generates a WARP registration on-device (X25519 keys never leave the phone),
  and produces an AmneziaWG (obfuscated) or plain WireGuard `.conf` you can save to Downloads,
  share or copy.
- **الاتصال داخل التطبيق:** run the generated file, or any `.conf` you import, with one button
  inside 2n5a (AmneziaWG library, no second VPN app). Hotspot sharing then goes through it.
- **شاشة ترحيب** with a typewriter animation (tap to skip).

## Requirements

Android 11+ (API 30), arm64, [Shizuku](https://shizuku.rikka.app/) 13.6.0+ for sharing.
Config generation needs only internet access.

## Build

The project root is the Gradle root. Do **not** open a subfolder.

```
go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init
# Go 1.27+, Android SDK, NDK 27.0.12077973
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Easiest: push to GitHub and let **Actions → Build** produce `2n5a-debug-apk`.

## Layout

| Path | Role |
|---|---|
| `app/src/main/java/dev/shizzi/warp/` | WARP provisioning, X25519, config writer, in-app tunnel (`TunnelController`, `ConfigStore`) |
| `app/src/main/java/dev/shizzi/ui/twon5a/` | Arabic RTL screens (home, settings, create files, welcome) |
| `datapath/` | Go netstack datapath (gomobile AAR) |
| everything else | Shizzi |

Cairo font is OFL (`app/src/main/assets/licenses/cairo-OFL.txt`).
