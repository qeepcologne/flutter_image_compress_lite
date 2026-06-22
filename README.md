# flutter_image_compress_lite

Standalone image-compression plugin for Flutter on **Android and iOS** — a replacement for [`flutter_image_compress`](https://github.com/fluttercandies/flutter_image_compress), collapsed into a single package (no federated architecture, hence no macOS/Web/OpenHarmony).

**Purpose:** minimal and legacy-free, for **current toolchains only** — minimal native dependencies, **no CocoaPods**, and built against the latest AGP, Gradle, Flutter, and Xcode rather than older ones. If you need older toolchains or CocoaPods, use the upstream package instead.

## What changed vs upstream

| | flutter_image_compress | flutter_image_compress_lite |
|---|---|---|
| Architecture | federated (3 packages) | standalone (1 package) |
| Platforms | Android, iOS, macOS, Web, OpenHarmony | Android, iOS |
| Dart / Flutter | >=2.12 / >=2.0 | ^3.11 / >=3.41 |
| **Image formats** | | |
| JPEG / PNG | yes | yes |
| HEIC / HEIF (iOS) | yes | yes |
| HEIC / HEIF (Android) | yes (heifwriter, API 28+) | yes (heifwriter, API 28+) |
| WebP (iOS) | encode + decode (SDWebImage) | decode only (native) |
| WebP (Android) | yes (native) | yes (native) |
| **Android** | | |
| Native deps | exifinterface, heifwriter, commons-io | **heifwriter only** |
| keepExif | androidx.exifinterface | **native android.media.ExifInterface** |
| Language | Java + Kotlin | Kotlin |
| minSdk | 21 | 24 |
| AGP | 8+ (Groovy) | 9+ only (Kotlin DSL) |
| **iOS** | | |
| Native deps | SDWebImage, SDWebImageWebPCoder, Mantle | **none** |
| keepExif | Mantle / SYMetadata | **native ImageIO** |
| Language | Objective-C | Swift 6 |
| Packaging | CocoaPods | **SPM only** |
| Deployment target | 9.0 | 15.0 |
| Xcode (to build) | any (no Swift floor) | 26+ (Swift 6.2 toolchain) |

## Usage

```yaml
dependencies:
  flutter_image_compress_lite: ^2.5.2
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

Same `FlutterImageCompress` method names and core parameters as the upstream — only a handful of legacy knobs have been dropped. If you used the defaults, step 1 is likely the only one that touches your code:

1. Swap the package name in `pubspec.yaml` and every `import` from `flutter_image_compress` to `flutter_image_compress_lite`.
2. Drop the `numberOfRetries` parameter from any call site. Decode-time `OutOfMemoryError` surfaces as a `COMPRESS_ERROR` `PlatformException`.
3. Drop the `inSampleSize` parameter from any call site.
4. If you catch on `Error` to handle `CompressError`, switch to `Exception` (or the bare `catch (e)`) — `CompressError implements Exception` here.

## Errors

`UnsupportedError` is thrown when the requested *encoding* is unsupported on the current platform:
- WebP encoding on iOS (decoding works)
- HEIC encoding on Android < API 28 (Android 9)

`CompressError` is thrown for invalid input caught Dart-side (empty image bytes, missing file).

`PlatformException` is thrown by the native side when something goes wrong below the channel. Both platforms surface the same core codes:
- `FILE_NOT_FOUND` — the source file could not be read.
- `BAD_IMAGE` — the source bytes/file could not be decoded into an image.
- `WRITE_FAILED` — the compressed output could not be written to the target path (`compressAndGetFile` only).

It can also carry these defensive codes (unreachable from the public Dart API):
- `BAD_ARGS` — channel arguments are missing, of the wrong type, or carry an unknown format index. Both platforms.
- `COMPRESS_ERROR` — catch-all native failure (Android: any uncaught exception, e.g. a HeifWriter error or an `OutOfMemoryError`; iOS: encoder-returns-nil edge case).

## License

Same as upstream: MIT
