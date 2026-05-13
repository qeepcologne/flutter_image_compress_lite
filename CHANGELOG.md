## 2.2.0

- **BREAKING (Android)**: HEIC encoding now requires API 30+ (Android 11), up from API 28. Calls on API 28/29 now throw `UnsupportedError` instead of attempting to encode.
- **Android**: dropped the `androidx.heifwriter:heifwriter:1.1.0` dependency. HEIC encoding now uses the platform-native `Bitmap.CompressFormat.HEIC` (available since API 30), going through the same path as JPEG/PNG/WebP. Removes a per-call temp file under `cacheDir`, the dedicated `HeifHandler`, and the `TmpFileUtil` helper.

Why: `androidx.heifwriter` is in maintenance, hasn't moved past 1.1.0 (March 2021), uses `MediaCodec` under the hood, and required a temp file on disk. The platform encoder is direct-to-`OutputStream`, ships with the OS, and has no transitive cost. API 28/29 (Android 9 / 10) is now ~5+ years old; raising the floor by two API levels is cheaper than carrying the legacy library.

## 2.1.1

- **iOS**: fix iOS build failure introduced in 2.1.0 — corrected selector capitalization to `HEIFRepresentationOfImage:format:colorSpace:options:` (was `heifRepresentationOfImage:`, which doesn't exist on `CIContext`).

## 2.1.0

User-visible fixes:

- **BREAKING**: `numberOfRetries` parameter on `compressWithFile` / `compressAndGetFile` renamed to `androidOomRetries`. The retry behavior was always Android-only (decode OOM → double `inSampleSize` and recurse); the new name reflects that. iOS ignores the value as before.
- **iOS**: WebP encoding now throws `UnsupportedError` up front instead of silently returning `null` (decoding still works on iOS 14+).
- **iOS**: HEIC encoding no longer writes through `NSTemporaryDirectory()` — uses `heifRepresentationOfImage:` directly. Removes a per-call temp-file leak.
- **Dart**: validator contract is now consistent — every entry point throws `UnsupportedError` for unsupported encodings (previously some returned `null`). The validator only checks the *output* format; input formats are auto-detected by the native decoder.

Internal cleanup:

- **Android**: introduced `CompressFormat` enum to replace `0/1/2/3` magic numbers throughout the handlers and `FormatRegister`.
- **Android**: `ExifKeeper` ported from Java to Kotlin; `settings.gradle` → `settings.gradle.kts`.
- **Android**: bumped Gradle wrapper to 9.5.0, `compileSdk` to 36.
- **Android**: removed dead code paths (`ResultHandler.replyError`, `ExifKeeper.copyExifToFile`, duplicate `Bitmap.compress` extensions, `System.gc()` in OOM retry, pre-Marshmallow `inDither` branch).
- **iOS**: introduced `ImageCompressFormat` `NS_ENUM` mirroring the Dart/Android enums.
- **iOS**: removed dead `getSystemVersion` Obj-C handler (Dart only calls Android for the API 28 check).
- **Dart**: dropped `part`/`part of` in favor of regular libraries with `import`/`export`; `CompressFormat.nativeValue` getter replaces the private `_convertTypeToInt` helper; default param values centralized in a private `_Defaults` class.

## 2.0.3

Merged `flutter_image_compress` + `flutter_image_compress_common` into a single standalone package.
No federated plugin architecture, no transitive dependencies with podspecs, no CocoaPods required.

- **BREAKING**: New package name `flutter_image_compress_lite` — change import
- **BREAKING**: Remove WebP encoding on iOS (decoding works natively on iOS 14+)
- **BREAKING**: Require Dart ^3.11.0, Flutter >=3.41.0, iOS 15.0+, Android minSdk 24, AGP 9+
- **iOS**: SPM only, zero third-party deps
- **iOS**: keepExif via native ImageIO (no Mantle)
- **Android**: Kotlin DSL, removed commons-io, bumped exifinterface 1.4.2, heifwriter 1.1.0

