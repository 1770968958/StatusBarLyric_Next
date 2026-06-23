# SuperLyricApi 3.4 Port

This fork was updated from the legacy SuperLyricApi 1.7 receiver API to SuperLyricApi 3.4.

Changed files:

- `gradle/libs.versions.toml`
  - `super-lyric-api` updated from `1.7` to `3.4`.
- `app/src/main/kotlin/statusbar/lyric/hook/module/SystemUILyric.kt`
  - Replaced `ISuperLyric` / `SuperLyricTool.registerSuperLyric(...)` with `ISuperLyricReceiver` / `SuperLyricHelper.registerReceiver(...)`.
  - Uses callback `publisher` as the music app package name.
  - Reads lyric text from `SuperLyricData.getLyric().getText()` via Kotlin properties.
  - Reads line duration from `SuperLyricLine.getDelay()` via Kotlin property.
  - Keeps Base64 icon support with a local decoder because `SuperLyricTool.base64ToBitmap` no longer exists in the new API.
- `app/proguard-rules.pro`
  - Keeps `com.hchen.superlyricapi` classes from R8 obfuscation.
- `.github/workflows/build_apk_manual.yml`
  - Adds a simple manual GitHub Actions workflow that builds and uploads a debug APK.
