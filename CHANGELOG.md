## 2.2.0

- **BREAKING (Android)**: HEIC encoding now requires API 30+ (Android 11), up from API 28. Calls on API 28/29 now throw `UnsupportedError` instead of attempting to encode.
- **Android**: dropped the `androidx.heifwriter:heifwriter:1.1.0` dependency. HEIC encoding now uses the platform-native `Bitmap.CompressFormat.HEIC` (available since API 30), going through the same path as JPEG/PNG/WebP. Removes a per-call temp file under `cacheDir`, the dedicated `HeifHandler`, and the `TmpFileUtil` helper.

Why: the platform encoder ships with the OS, has no transitive cost, and writes direct-to-`OutputStream` (heifwriter required a temp file under `cacheDir`). HEIC now reuses the same `CommonHandler` path as JPEG/PNG/WebP — one less dependency, one less code path, no per-call disk I/O. The price is raising the HEIC floor from API 28 to 30 (Android 9/10 → Android 11), which we judged acceptable for the simplification.

Internal cleanup:

- **iOS**: rewrote the plugin in Swift. The 4 Obj-C `.m` files + 7 `.h` headers are replaced by 4 Swift files (`ImageCompressPlugin.swift`, `Compressor.swift`, `ExifKeeper.swift`, `UIImage+Scale.swift`). No `__bridge` casts, no manual `CFRelease`, no `include/` public-headers folder, no `// Created by cjl …` fork comments. The two near-identical `compressWithUIImage:` / `compressDataWithUIImage:` methods collapse into one. File-existence and image-decode failures now surface as `FlutterError` (`FILE_NOT_FOUND`, `BAD_IMAGE`) instead of propagating nil. `UIGraphicsBeginImageContext` (deprecated since iOS 10) is replaced with `UIGraphicsImageRenderer`; the rotated bounding box is now computed via `CGRect.applying(_:)` instead of allocating a `UIView`.
- **Android**: `ResultHandler`'s thread pool is now owned by the plugin instance and `shutdown()` in `onDetachedFromEngine` (was a `companion object` `Executors.newFixedThreadPool(8)` that lived for the process lifetime). Re-armed on re-attach.
- **Android**: dropped the `androidx.exifinterface` dependency in favor of the platform `android.media.ExifInterface` (available since API 24, our minSdk). EXIF reads/writes go through the framework class with a 6-line orientation→degrees mapping. Plugin now has **zero third-party Android dependencies** (matches iOS).
- **Android**: collapsed 13 Kotlin files into 3 — `ImageCompressPlugin.kt`, `Compressor.kt`, `Exif.kt` — to mirror the iOS Swift layout. Dropped the unused `FormatHandler` interface, `FormatRegister` map, never-thrown `CompressError`, the now-empty `AndroidManifest.xml` (AGP synthesizes from `namespace`), and the `tools:overrideLibrary="androidx.heifwriter"` workaround.
- **Android**: `BitmapFactory` decode now uses `ARGB_8888` for PNG/WebP/HEIC and keeps `RGB_565` for JPEG. Previously all formats decoded as `RGB_565`, silently dropping the alpha channel for transparency-capable outputs.
- **Android**: `CompressFileHandler.handle` reads EXIF rotation from `File(path)` directly instead of loading the full file into a `ByteArray` first.
- **Android**: `FormatRegister` is now self-initializing (singleton map populated at class load) — removes the `init { }` block in `ImageCompressPlugin` that re-registered handlers on every engine attach.
- **Android**: unknown format index now responds with `result.error("UNKNOWN_FORMAT", …)` instead of `result.success(null)`. In practice unreachable because the Dart enum can't produce an unknown index, but no longer fails invisibly if the wire format ever desyncs.

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

