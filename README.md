# flutter_image_compress_lite

Standalone image-compression plugin for Flutter on **Android and iOS** — a drop-in replacement for [`flutter_image_compress`](https://github.com/fluttercandies/flutter_image_compress), collapsed into a single package (no federated architecture, hence no macOS/Web/OpenHarmony).

**Purpose:** minimal and legacy-free, for **current toolchains only** — minimal native dependencies (no CocoaPods, no SDWebImage/Mantle), built against the latest AGP, Gradle, Flutter, and Xcode rather than older ones. If you need older toolchains or CocoaPods, use the upstream package instead.

## What changed vs upstream

| | flutter_image_compress | flutter_image_compress_lite |
|---|---|---|
| iOS deps | SDWebImage, SDWebImageWebPCoder, Mantle | **none** |
| Android deps | exifinterface, heifwriter, commons-io | **heifwriter only** |
| CocoaPods required | yes (transitive) | **no** |
| JPEG/PNG | yes | yes |
| iOS HEIC/HEIF | yes | yes |
| Android HEIC/HEIF | yes (heifwriter, API 28+) | yes (heifwriter, API 28+) |
| iOS WebP decoding | via SDWebImage | native (iOS 14+) |
| iOS WebP encoding | via SDWebImage | not supported |
| iOS keepExif | via Mantle/SYMetadata | native ImageIO |
| Android keepExif | via androidx.exifinterface | native android.media.ExifInterface |
| iOS language | Objective-C | Swift 6 |
| Android language | Java + Kotlin | Kotlin |
| iOS packaging | CocoaPods | SPM only |
| iOS deployment target | 9.0 | 15.0 |
| Xcode (to build iOS) | any (no Swift floor) | 26+ (Swift 6.2 toolchain) |
| Android minSdk | 21 | 24 |
| AGP | 8+ (Groovy) | 9+ only (Kotlin DSL) |
| Dart/Flutter | >=2.12/>=2.0 | ^3.11/>=3.41 |
| Architecture | federated (3 packages) | standalone (1 package) |
| Platforms | Android, iOS, macOS, Web, OpenHarmony | Android, iOS |

## Usage

```yaml
dependencies:
  flutter_image_compress_lite: ^2.3.0
```

```dart
import 'package:flutter_image_compress_lite/flutter_image_compress_lite.dart';

final result = await FlutterImageCompress.compressAndGetFile(
  sourcePath,
  targetPath,
);
```

Same `FlutterImageCompress` API as the upstream — just change the import.

> **Build requirement:** the iOS side is written against the Swift 6.2 toolchain (approachable concurrency / `@concurrent`), so building for iOS requires **Xcode 26 or newer**. The runtime floor is unchanged — the compiled plugin still runs on **iOS 15+**.

## Errors

`UnsupportedError` is thrown when the requested *encoding* is unsupported on the current platform:
- WebP encoding on iOS (decoding works on iOS 14+)
- HEIC encoding on Android < API 28 (Android 9)

`CompressError` is thrown for invalid input caught Dart-side (empty image bytes, missing file, `androidOomRetries <= 0`).

`PlatformException` is thrown by the native side when something goes wrong below the channel. Both platforms surface the same core codes (Android previously swallowed these to `null`; it now matches iOS):
- `FILE_NOT_FOUND` — the source file could not be read.
- `BAD_IMAGE` — the source bytes/file could not be decoded into an image.
- `WRITE_FAILED` — the compressed output could not be written to the target path (`compressAndGetFile` only).

Platform-specific, defensive codes (unreachable from the public Dart API):
- **Android** — `UNKNOWN_FORMAT` (wire format index doesn't match a known `CompressFormat`) and `COMPRESS_ERROR` (any other native failure, e.g. a HEIC-encoder error).
- **iOS** — `BAD_ARGS` (malformed channel arguments).

## License

Same as upstream: MIT
