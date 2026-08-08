# app/libs — native core (not committed)

This folder holds the Xray tunnel core that the app links against. The binaries are
**deliberately not committed** (see the root `.gitignore`: `app/libs/*.aar`) because they
are large prebuilt artifacts produced by a separate toolchain, not source we edit here.

## What belongs here

- `libv2ray.aar` — the Xray core wrapped for Android. It's a Go program compiled to an
  Android library with `gomobile bind`. The build wires it in via `settings.gradle.kts`
  (`flatDir { dirs("app/libs") }`) and the (currently commented-out) dependency in
  `app/build.gradle.kts`:

  ```kotlin
  // implementation(files("libs/libv2ray.aar"))
  ```

Phases 0–4 (data layer) and the current UI build **without** this file — the dependency is
commented out on purpose, so CI produces a working debug APK today. It becomes required in
**Phase 5**, when the VpnService starts the tunnel.

## How to obtain `libv2ray.aar`

It is built from source — it cannot be generated from this repo. The upstream wrapper is
`2dust/AndroidLibXrayLite`, which runs `gomobile bind` against Xray-core. Building it needs
Go, the Android NDK, and the gomobile toolchain. See that project's own README for the exact
command and the toolchain versions it expects.

Once you have `libv2ray.aar`, drop it in this folder and uncomment the dependency above.

> Do not commit the `.aar`. Keep it local (or attach it to a build as needed) so the repo
> stays source-only.
