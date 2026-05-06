## 2.0.4

User-visible fixes:

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
- **Dart**: dropped `part`/`part of` in favor of regular libraries with `import`/`export`; `CompressFormat.nativeValue` getter replaces the private `_convertTypeToInt` helper.

## 2.0.3

Merged `flutter_image_compress` + `flutter_image_compress_common` into a single standalone package.
No federated plugin architecture, no transitive dependencies with podspecs, no CocoaPods required.

- **BREAKING**: New package name `flutter_image_compress_lite` — change import
- **BREAKING**: Remove WebP encoding on iOS (decoding works natively on iOS 14+)
- **BREAKING**: Require Dart ^3.11.0, Flutter >=3.41.0, iOS 15.0+, Android minSdk 24, AGP 9+
- **iOS**: SPM only, zero third-party deps
- **iOS**: keepExif via native ImageIO (no Mantle)
- **Android**: Kotlin DSL, removed commons-io, bumped exifinterface 1.4.2, heifwriter 1.1.0

