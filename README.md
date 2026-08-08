# V2RayTunKMZ

An Android proxy/VPN client for **vmess / vless / shadowsocks / trojan** share links.
Single-activity Jetpack Compose UI, Room persistence, kotlinx-serialization parsing.

- Package: `com.kmz.v2raytun`
- minSdk 24, compileSdk / targetSdk 35, Kotlin 2.0.21, AGP 8.7.3, JVM 17

## Status

- **Done — data layer:** share-link parsers (single link, newline list, base64 subscription),
  Room storage, repository, clipboard import. Unit-tested under `app/src/test`.
- **Done — UI:** Material 3 theme (light/dark + dynamic color), server list, import,
  delete-with-confirm, connect bar.
- **Pending — Phase 5:** the tunnel engine (VpnService + Xray core). See
  [`app/libs/README.md`](app/libs/README.md). Connect currently shows an honest
  "ships in Phase 5" message.

## Building

CI builds the debug APK on every push (see below). To build **locally** you need JDK 17 and
Gradle 8.10 (AGP 8.7.3 requires Gradle 8.9+). This repo does not commit a Gradle wrapper jar;
use a system `gradle`, or run `gradle wrapper --gradle-version 8.10` once to create one.

```
gradle :app:testDebugUnitTest   # run parser unit tests
gradle :app:assembleDebug       # build the debug APK
```

The APK lands in `app/build/outputs/apk/debug/`.

## CI (GitHub Actions)

[`.github/workflows/android.yml`](.github/workflows/android.yml) runs on every push: it sets
up JDK 17, the Android SDK, and Gradle 8.10, runs the unit tests, builds the debug APK, and
uploads it as a build artifact. Download it from the run's **Artifacts** section
(`v2raytunkmz-debug-apk`).
