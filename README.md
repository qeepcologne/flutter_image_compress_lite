# flutter_image_compress_lite

Standalone image-compression plugin for Flutter on **Android and iOS** — a drop-in replacement for [`flutter_image_compress`](https://github.com/fluttercandies/flutter_image_compress), collapsed into a single package (no federated architecture, hence no macOS/Web/OpenHarmony).

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
| WebP (iOS) | encode + decode (SDWebImage) | decode only (native, iOS 14+) |
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
  flutter_image_compress_lite: ^2.3.1
```

```dart
import 'package:flutter_image_compress_lite/flutter_image_compress_lite.dart';

final result = await FlutterImageCompress.compressAndGetFile(
  sourcePath,
  targetPath,
);
```

Same `FlutterImageCompress` API as the upstream — just change the import.

## Errors

`UnsupportedError` is thrown when the requested *encoding* is unsupported on the current platform:
- WebP encoding on iOS (decoding works on iOS 14+)
- HEIC encoding on Android < API 28 (Android 9)

`CompressError` is thrown for invalid input caught Dart-side (empty image bytes, missing file, `androidOomRetries <= 0`).

`PlatformException` is thrown by the native side when something goes wrong below the channel. Both platforms surface the same core codes (Android previously swallowed these to `null`; it now matches iOS):
- `FILE_NOT_FOUND` — the source file could not be read.
- `BAD_IMAGE` — the source bytes/file could not be decoded into an image.
- `WRITE_FAILED` — the compressed output could not be written to the target path (`compressAndGetFile` only).

It can also carry these platform-specific, defensive codes (unreachable from the public Dart API):
- **Android** — `UNKNOWN_FORMAT` (wire format index doesn't match a known `CompressFormat`) and `COMPRESS_ERROR` (any other native failure, e.g. a HEIC-encoder error).
- **iOS** — `BAD_ARGS` (malformed channel arguments).

## License

Same as upstream: MIT
