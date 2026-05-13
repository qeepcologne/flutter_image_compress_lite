# flutter_image_compress_lite

Standalone image compression plugin for Flutter (Android + iOS only) — a legacy-free drop-in replacement for `flutter_image_compress` with zero third-party platform dependencies on either Android or iOS, and no CocoaPods.

**Platforms:** Android, iOS. No macOS/Web/OpenHarmony support (unlike the upstream federated plugin).

## What changed vs upstream

Based on [flutter_image_compress](https://github.com/fluttercandies/flutter_image_compress), merged into a single standalone package (no federated plugin architecture).

| | flutter_image_compress | flutter_image_compress_lite |
|---|---|---|
| iOS deps | SDWebImage, SDWebImageWebPCoder, Mantle | **none** |
| Android deps | exifinterface, heifwriter, commons-io | **none** |
| CocoaPods required | yes (transitive) | **no** |
| JPEG/PNG | yes | yes |
| iOS HEIC/HEIF | yes | yes |
| Android HEIC/HEIF | yes (heifwriter, API 28+) | yes (native, API 30+) |
| iOS WebP decoding | via SDWebImage | native (iOS 14+) |
| iOS WebP encoding | via SDWebImage | not supported |
| iOS keepExif | via Mantle/SYMetadata | native ImageIO |
| Android keepExif | via androidx.exifinterface | native android.media.ExifInterface |
| iOS language | Objective-C | Swift |
| Android language | Java + Kotlin | Kotlin |
| iOS packaging | CocoaPods | SPM only |
| iOS deployment target | 9.0 | 15.0 |
| Android minSdk | 21 | 24 |
| AGP | 8+ (Groovy) | 9+ only (Kotlin DSL) |
| Dart/Flutter | >=2.12/>=2.0 | ^3.11/>=3.41 |
| Architecture | federated (3 packages) | standalone (1 package) |
| Platforms | Android, iOS, macOS, Web, OpenHarmony | Android, iOS |

## Usage

```yaml
dependencies:
  flutter_image_compress_lite: ^2.2.0
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
- HEIC encoding on Android < API 30 (Android 11)

`CompressError` is thrown for invalid input caught Dart-side (empty image bytes, missing file, `androidOomRetries <= 0`).

`PlatformException` is thrown by the native side when something goes wrong below the channel:
- **iOS** — `FILE_NOT_FOUND` (could not read the file) or `BAD_IMAGE` (could not decode it).
- **Android** — `UNKNOWN_FORMAT` if the wire format index doesn't match a known `CompressFormat` (defensive; unreachable from the public Dart API).

## License

Same as upstream: MIT
