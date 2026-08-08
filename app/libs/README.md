# app/libs — native core (not committed)

This folder holds the Xray tunnel core the app links against. The binaries are **deliberately
not committed** (see the root `.gitignore`: `app/libs/*.aar`) because they are large prebuilt
artifacts from a separate toolchain, not source we edit here.

## What belongs here

- `libv2ray.aar` — the Xray core wrapped for Android: a Go program compiled to an Android
  library with `gomobile bind`.

## Absent is a supported state

Nothing needs uncommenting. `app/build.gradle.kts` checks for the file and adapts:

| | `.aar` present | `.aar` absent |
|---|---|---|
| dependency | linked | skipped, with a log line |
| compiled source dir | `src/withCore/java` | `src/noCore/java` |
| `BuildConfig.HAS_CORE` | `true` | `false` |
| engine at runtime | the real Xray core | `MissingTunnelCore` |

The swap is a whole source directory rather than a flag because the binding code names classes
that exist only inside the `.aar` — a clean clone has to compile without them. `MissingTunnelCore`
throws on `start()` instead of pretending to connect, so a coreless build cannot masquerade as a
working tunnel.

## How to obtain `libv2ray.aar`

Run the **Tunnel core** workflow (`.github/workflows/core.yml`) from the Actions tab. It builds
the core on GitHub's runners — Go and the Android NDK are a large download, and keeping them off
your machine is the whole reason this project builds in CI.

The run produces two artifacts:

- `libv2ray-aar` — the binary. Download it into this folder.
- `libv2ray-report` — provenance (upstream commit and date), upstream's repo layout, and a
  `javap` dump of the `.aar`'s public API. The same report is rendered on the run's summary
  page, so it can be read in a browser without downloading anything.

Upstream is [`2dust/AndroidLibXrayLite`](https://github.com/2dust/AndroidLibXrayLite). The
workflow takes the Go version from upstream's `go.mod` and the NDK from the runner instead of
pinning versions this repo cannot verify, and it reports upstream's layout on every run — so
when a build step is wrong, the same run tells you what to correct.

> Do not commit the `.aar`. Keep it local so the repo stays source-only.
