## 2.6.0+2

- Cosmetic: Dart source cleanup (arrow syntax, dead intermediates). No behavior change.

## 2.6.0

- **Breaking**: `compressWithFile`, `compressAndGetFile`, and `compressAssetImage` return non-nullable types (`Uint8List`, `XFile`, `Uint8List`). The native sides have always thrown `PlatformException` on failure since 2.5.2 and never delivered `null` on the happy path — the `?` was leftover from the pre-2.5.2 contract. Source-only break for callers using `?? fallback` or `if (result != null)`.
- **Breaking**: `compressAssetImage` now throws `CompressError` for an empty asset, to match the empty-bytes handling in `compressWithList` (was: returned `null`).

## 2.5.5

- **iOS**: Xcode floor raised to **26.4.1** (Swift 6.3 toolchain). `Package.swift` declares `swift-tools-version: 6.3`; no code change.
- **Build**: Gradle wrapper 9.6.0 → 9.6.1.

## 2.5.4+1

- README: added Android SDK to the list of "latest toolchains" the package is built against, and reordered the list to mirror the comparison table (Flutter → Android trio → Xcode).

## 2.5.4

- **Environment**: declared minima moved to Flutter `3.44.0` and Dart `3.12.0`. The plugin's `android/build.gradle.kts` applies only `com.android.library` (no `kotlin-android`), which already required AGP 9's built-in Kotlin support — AGP 9 is the default in Flutter 3.44+, not earlier. The previous `>=3.41.0` floor was honored by pub.dev but not actually buildable without manual AGP 9 opt-in on the host. The new floor matches what the build always required. Follows the [Flutter built-in Kotlin migration guide for plugin authors](https://docs.flutter.dev/release/breaking-changes/migrate-to-built-in-kotlin/for-plugin-authors).
- **iOS internal**: replaced `NSLog` with the modern `os.Logger` API (subsystem `com.qeepcologne.flutter_image_compress_lite`, categories `compress` and `scale`). When `showNativeLog` is on, output is now filterable by subsystem/category in Console.app and goes through the ring-buffered unified logging system. No observable behavior change.
- **Android internal**: routed all exception logging through `Log.w(tag, msg, throwable)` instead of `e.printStackTrace()` (which writes to stderr and often gets swallowed by zygote). Also pulled the `"flutter_image_compress"` tag literal into a `LOG_TAG` constant. Stack traces from `replyCatching` and the EXIF copy fallback now appear in logcat under the same tag as everything else.

## 2.5.3

- **Android**: fix 2.5.2 build break — `ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY` was used by the EXIF keeper, but that constant lives only on `androidx.exifinterface.media.ExifInterface`; the framework `android.media.ExifInterface` (which we use since 2.2.0) only exposes the deprecated `TAG_ISO_SPEED_RATINGS`. Reverted to that with `@Suppress("DEPRECATION")`. Same tag-id (34855), same wire bytes.

## 2.5.2

- **iOS**: encoder-returns-nil edge cases (missing `cgImage`, HEIF encoder failure) now surface as `COMPRESS_ERROR` `PlatformException` instead of resolving the Dart Future to nil — keeps the non-nullable `Future<Uint8List>` return type of `compressWithList` honest. Unreachable in practice for normal inputs.
- **Internal**: swapped EXIF string literals for framework constants in both EXIF keepers (Kotlin `ExifInterface.TAG_*`, Swift `CGImagePropertyOrientation.up.rawValue`) and dropped the now-unreachable `Outcome.null` enum case on iOS. No observable behavior change.

## 2.5.1

- **iOS**: fix compression producing output 4–9× larger than the requested `minWidth`/`minHeight` (and JPEG encoding correspondingly slower) on Retina devices. The resize and rotate steps used `UIGraphicsImageRenderer(size:)` without a format, which defaulted to the main screen's UIKit scale (2× / 3×) — so the renderer's actual pixel bitmap was `target × screenScale`. Now forces `format.scale = 1`. Reported in #4.
- **Android**: bumped the Gradle wrapper 9.5.1 → 9.6.0.

## 2.5.0

- **BREAKING**: removed the `inSampleSize` parameter from `compressWithList`, `compressWithFile`, and `compressAndGetFile`. It was an Android-only `BitmapFactory.Options` knob (iOS ignored it) from the 32-bit ART / 128 MB-heap era. On modern Android (API 26+) bitmaps live in native heap and devices with 50 MP cameras have 8–12 GB RAM, so the memory savings no longer pay for the leaky abstraction or the silent quality loss when callers pick a value that under-samples small inputs. The `OutOfMemoryError` catch added in 2.4.0 still surfaces decode-time OOM as a `COMPRESS_ERROR` `PlatformException`. Callers that passed the argument need to drop it.
- **BREAKING**: removed `CompressFormat.nativeValue`. It was a getter that just returned `index` — the wire value is the enum's built-in ordinal. Callers reading `format.nativeValue` need to switch to `format.index`.
- **BREAKING**: `CompressError` now implements `Exception` instead of extending `Error`. The failures it carries (empty bytes, missing source file, same source-and-target path) are recoverable user-input conditions, not programming bugs. Code using `try { … } on Exception` will now catch it (previously had to use `on Error` or the bare `catch (e)`).
- **Internal**: inlined the `FlutterImageCompressValidator` class into a private top-level function in the main library — one less file, one less indirection, no public-API change.
- **iOS internal**: dropped the dead `@objc` on `ImageCompressPlugin.showLog`. No Obj-C consumer existed; the annotation was carried over from the 2.2.0 Obj-C→Swift rewrite. The class itself is still `@objc(ImageCompressPlugin)` because Flutter's plugin discovery requires it.

## 2.4.4

- **Android**: unified the wire-format error code with iOS — `UNKNOWN_FORMAT` is renamed to `BAD_ARGS`, and a malformed channel argument list (wrong type, missing element, null) now also surfaces as `BAD_ARGS` instead of crashing the executor thread and hanging the Dart Future. Real callers can't trip this — the Dart side constrains both shape and enum range — so the rename is documentation-only in practice.

## 2.4.3

- Docs-only — added the missing library / `CompressFormat` / `CompressError` doc comments, an `example/main.dart`, and a note on the class-level doc that `minWidth` / `minHeight` are lower bounds on the output (image is downscaled with aspect ratio preserved so both axes end up ≥ the requested minimum; never upscaled). No API or behavior change.

## 2.4.2

- Removed the debug-mode filename-extension assert (and the `CompressFormat.suffixes` field that backed it). The check only fired in debug; release builds were never affected.

## 2.4.1

- Accept `.heif` as a valid target extension for HEIC encoding alongside `.heic` — same container bytes, different naming convention. Previously tripped the debug-mode filename assert.

## 2.4.0

- **BREAKING**: removed the `androidOomRetries` parameter from `compressWithFile` and `compressAndGetFile`. The old retry-on-OOM logic was Android-only and silently produced an empty result when retries exhausted; now an `OutOfMemoryError` surfaces as a `COMPRESS_ERROR` `PlatformException` like other native failures. Callers that passed the argument need to drop it.

## 2.3.1

- Docs and metadata only — README cleanup, package description, and a fork copyright line. No API or behavior changes.

## 2.3.0

Internal cleanup — no public API changes.

- **Android (behavior change)**: read/decode/write failures now throw a `PlatformException` instead of silently resolving to `null`, matching iOS. New wire codes: `FILE_NOT_FOUND`, `BAD_IMAGE`, `WRITE_FAILED`, plus a catch-all `COMPRESS_ERROR`. Callers that previously branched on a `null` result will now see an exception for genuinely broken input. See the README "Errors" section.
- **Android**: the three `compress*` handlers now share a single index-driven `CompressArgs` parser (mirroring the iOS `CompressParams`) and a `replyCatching` helper, replacing the per-handler positional unpacking and try/catch. No wire-format change.
- **Dart**: `compressWithFile` / `compressAndGetFile` now check source existence with async `File.exists()` instead of `existsSync()`, so the entry points no longer block the isolate on filesystem I/O.
- **iOS**: migrated the method-channel handlers to Swift structured concurrency (Swift 6.2). The manual `DispatchQueue.global(qos:).async { … }` hop is replaced by a `Task` calling a single `@concurrent` `run(_:)` worker; the three near-identical handlers collapse into a `Sendable` `Request` parser plus that one worker. Arguments are now read out of `FlutterMethodCall` synchronously on the calling thread, so no non-`Sendable` Flutter type crosses the concurrency boundary. Builds under the Swift 6 language mode with strict concurrency.
- **iOS BUILD REQUIREMENT**: building for iOS now requires **Xcode 26+** (Swift 6.2 toolchain). The runtime floor is unchanged — still **iOS 15+** (Swift concurrency back-deploys; no iOS-18-only APIs such as `Mutex` are used).

## 2.2.0

Internal cleanup — no public API or behavior changes.

- **iOS**: rewrote the plugin in Swift. The 6 Obj-C `.m` files + 7 `.h` headers are replaced by 4 Swift files (`ImageCompressPlugin.swift`, `Compressor.swift`, `ExifKeeper.swift`, `UIImage+Scale.swift`). No `__bridge` casts, no manual `CFRelease`, no `include/` public-headers folder, no `// Created by cjl …` fork comments. The two near-identical `compressWithUIImage:` / `compressDataWithUIImage:` methods collapse into one. File-existence and image-decode failures now surface as `FlutterError` (`FILE_NOT_FOUND`, `BAD_IMAGE`) instead of propagating nil. `UIGraphicsBeginImageContext` (deprecated since iOS 10) is replaced with `UIGraphicsImageRenderer`; the rotated bounding box is now computed via `CGRect.applying(_:)` instead of allocating a `UIView`.
- **Android**: `ResultHandler`'s thread pool is now owned by the plugin instance and `shutdown()` in `onDetachedFromEngine` (was a `companion object` `Executors.newFixedThreadPool(8)` that lived for the process lifetime). Re-armed on re-attach.
- **Android**: dropped the `androidx.exifinterface` dependency in favor of the platform `android.media.ExifInterface` (available since API 24, our minSdk). EXIF reads/writes go through the framework class with a 6-line orientation→degrees mapping. `androidx.heifwriter:heifwriter:1.1.0` remains — HEIC encoding still goes through it (no platform-native HEIC encoder exists in `Bitmap.CompressFormat`).
- **Android**: collapsed 13 Kotlin files into 3 — `ImageCompressPlugin.kt`, `Compressor.kt`, `Exif.kt` — to mirror the iOS Swift layout. Dropped the unused `FormatHandler` interface, `FormatRegister` map, and the never-thrown `CompressError`.
- **Android**: `BitmapFactory` decode now uses `ARGB_8888` for PNG/WebP/HEIC and keeps `RGB_565` for JPEG. Previously all formats decoded as `RGB_565`, silently dropping the alpha channel for transparency-capable outputs.
- **Android**: `CompressFileHandler.handle` reads EXIF rotation from `File(path)` directly instead of loading the full file into a `ByteArray` first.
- **Android**: unknown format index now responds with `result.error("UNKNOWN_FORMAT", …)` instead of `result.success(null)`. In practice unreachable because the Dart enum can't produce an unknown index, but no longer fails invisibly if the wire format ever desyncs.
- **Android**: bumped `compileSdk` 36 → 37 (Android 17). No `minSdk` change. CI environments may need the API 37 platform package installed.

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

