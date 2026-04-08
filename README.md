# flutter_image_compress_lite

Standalone image compression plugin for Flutter (Android + iOS only) — drop-in replacement for `flutter_image_compress` with no third-party iOS dependencies and no CocoaPods requirement.

**Platforms:** Android, iOS. No macOS/Web/OpenHarmony support (unlike the upstream federated plugin).

## What changed vs upstream

Based on [flutter_image_compress](https://github.com/fluttercandies/flutter_image_compress), merged into a single standalone package (no federated plugin architecture).

| | flutter_image_compress | flutter_image_compress_lite |
|---|---|---|
| iOS deps | SDWebImage, SDWebImageWebPCoder, Mantle | **none** |
| Android deps | exifinterface, heifwriter, commons-io | exifinterface, heifwriter |
| iOS WebP decoding | via SDWebImage | native (iOS 14+) |
| iOS WebP encoding | via SDWebImage | not supported |
| HEIC/HEIF | yes | yes |
| JPEG/PNG | yes | yes |
| keepExif (iOS) | via Mantle/SYMetadata | native ImageIO |
| keepExif (Android) | via ExifInterface | via ExifInterface |
| iOS packaging | CocoaPods | SPM only |
| AGP | 8+ (Groovy) | 9+ only (Kotlin DSL) |
| iOS deployment target | 9.0 | 15.0 |
| Android minSdk | 21 | 24 |
| Dart/Flutter | >=2.12/>=2.0 | ^3.11/>=3.41 |
| Architecture | federated (3 packages) | standalone (1 package) |
| CocoaPods required | yes (transitive) | **no** |
| Platforms | Android, iOS, macOS, Web, OpenHarmony | Android, iOS |

## Usage

```yaml
dependencies:
  flutter_image_compress_lite: ^2.0.0
```

```dart
import 'package:flutter_image_compress_lite/flutter_image_compress_lite.dart';

final result = await FlutterImageCompress.compressAndGetFile(
  sourcePath,
  targetPath,
);
```

Same `FlutterImageCompress` API as the upstream — just change the import.

## License

Same as upstream: MIT
