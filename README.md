# flutter_image_compress_lite

Standalone image-compression plugin for Flutter on **Android and iOS** — a replacement for [`flutter_image_compress`](https://github.com/fluttercandies/flutter_image_compress), collapsed into a single package (no federated architecture, hence no macOS/Web/OpenHarmony).

**Purpose:** minimal and legacy-free, for **current toolchains only** — minimal native dependencies, **no CocoaPods**, and built against the latest Flutter, AGP, Gradle, Android SDK, and Xcode rather than older ones. If you need older toolchains or CocoaPods, use the upstream package instead.

## What changed vs upstream

| | flutter_image_compress | flutter_image_compress_lite |
|---|---|---|
| Architecture | federated (6 packages) | standalone (1 package) |
| Platforms | Android, iOS, macOS, Web, OpenHarmony | Android, iOS |
| Dart / Flutter | >=2.12 / >=2.0 | ^3.13 / >=3.47 |
| **Image formats** | | |
| JPEG / PNG | yes | yes |
| HEIC / HEIF (iOS) | yes | yes |
| HEIC / HEIF (Android) | yes (heifwriter, API 28+) | yes (heifwriter, API 28+) |
| WebP (iOS) | encode + decode (SDWebImage) | decode only (native) |
| WebP (Android) | yes (native) | yes (native) |
| AVIF (iOS) | decode only, iOS 16+ (native) | decode only, iOS 16+ (native) |
| AVIF (Android) | decode only, API 31+ (native) | decode API 31+, **encode API 34+** (heifwriter) |
| **Android** | | |
| Native deps | exifinterface 1.4.2, heifwriter 1.0.0, commons-io 2.16.1 | **heifwriter 1.1.0 only** |
| keepExif | JPEG/PNG/WebP (androidx.exifinterface); allow-list of ~92 tags | JPEG/PNG/WebP (android.media.ExifInterface; PNG API 30+, WebP API 31+); every tag the framework knows minus source-only ones, so ~30 more survive |
| Language | Java + Kotlin | Kotlin |
| minSdk / compileSdk | 21 / 34 | 24 / 37 |
| AGP | 7.4+ (Groovy), guards `kotlin-android` on AGP 9 | 9+ only (Kotlin) |
| **iOS** | | |
| Native deps | SDWebImage, SDWebImageWebPCoder | **none** |
| keepExif | full source metadata via ImageIO passthrough, source-only keys stripped | same |
| Language | Objective-C | Swift 6.3 |
| Packaging | CocoaPods + SPM | **SPM only** |
| Deployment target | 9.0 | 15.0 |
| Xcode (to build) | any | 26.4.1+ |

### Size

Dropping the pods is worth **~0.7 MB** on a release iOS app bundle. On **Android there is no difference** — neither
version ships native libraries there, and R8 strips upstream's extra `commons-io` / `exifinterface` code.

Don't derive the saving from the raw dependency sizes: SDWebImage, SDWebImageWebPCoder and Mantle are far larger than
0.7 MB as sources, but dead-code stripping, obfuscation, resource shrinking and App Store compression absorb most of
that before it reaches a bundle.

### Android host-app requirement

The plugin sets no `compileOptions` / `jvmTarget` of its own — the host app decides one JVM target for every module.
Either enable AGP 9's built-in Kotlin (`android.builtInKotlin=true`, where Kotlin's target defaults to
`compileOptions.targetCompatibility`), or apply the target to all modules from the root build file, e.g.

```kotlin
subprojects {
    afterEvaluate {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            compilerOptions.jvmTarget = JvmTarget.fromTarget(<your java version>)
        }
    }
}
```

Without either, KGP compiles the module against the running JDK while javac uses AGP's default and the build fails with
*"Inconsistent JVM-target compatibility between Java and Kotlin tasks"*. Note Flutter's app template writes
`android.builtInKotlin=false`, so a stock app needs the root-level target.

Also fixes long-standing Android upstream bugs — most visibly JPEG gradient banding on decode and EXIF-orientation flip variants that came out mirrored — see the [CHANGELOG](CHANGELOG.md) for the full list.

## Usage

```yaml
dependencies:
  flutter_image_compress_lite: ^2.9.0
```

```dart
import 'package:flutter_image_compress_lite/flutter_image_compress_lite.dart';

final result = await FlutterImageCompress.compressAndGetFile(
  sourcePath,
  targetPath,
);
```

See [`example/main.dart`](example/main.dart) for the bytes-in / HEIC / WebP / asset variants.

## Migrating from flutter_image_compress

Same `FlutterImageCompress` method names and core parameters as the upstream. A handful of legacy parameters, a custom exception class, and the nullable-return contract have been dropped; the rest is unchanged. If you used the defaults, step 1 is likely the only one that touches your code:

1. Swap the package name in `pubspec.yaml` and every `import` from `flutter_image_compress` to `flutter_image_compress_lite`.
2. Drop the `numberOfRetries`, `inSampleSize`, and `autoCorrectionAngle` parameters from any call site. Decode-time `OutOfMemoryError` surfaces as a `COMPRESS_ERROR` `PlatformException`; EXIF orientation is always honored on both platforms.
3. Replace any `on CompressError catch (e)` with `on ArgumentError catch (e)` (or bare `catch (e)`). Dart-side input-validation now throws the standard `dart:core` `ArgumentError`; a bare catch — or `on Error catch (e)`, already required to handle the `UnsupportedError` cases below — covers it.
4. Drop null-checks on the return values of `compressWithFile`, `compressAndGetFile`, and `compressAssetImage` — these throw on failure here (and treat empty input as a failure) rather than returning `null`.

## Error handling

`UnsupportedError` is thrown when the requested *encoding* is unsupported on the current platform:
- WebP encoding on iOS (decoding works)
- HEIC encoding on Android < API 28 (Android 9)

`ArgumentError` is thrown for invalid input caught Dart-side (empty image bytes, empty asset, missing file, same source and target path).

`PlatformException` is thrown by the native side when something goes wrong below the channel:
- `FILE_NOT_FOUND` — the source file could not be read.
- `BAD_IMAGE` — the source bytes/file could not be decoded into an image.
- `WRITE_FAILED` — the compressed output could not be written to the target path (`compressAndGetFile` only).
- `COMPRESS_ERROR` — catch-all native failure. On Android: any uncaught exception during encode, e.g. a `HeifWriter` failure or an `OutOfMemoryError` on a very large input. On iOS: encoder-returns-nil edge case, unreachable in practice.
- `BAD_ARGS` — channel arguments are missing, of the wrong type, or carry an unknown format index; unreachable from the public Dart API.

## License

Same as upstream: MIT
