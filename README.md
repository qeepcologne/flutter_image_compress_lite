# flutter_image_compress_lite

Lite fork of [flutter_image_compress_common](https://github.com/fluttercandies/flutter_image_compress) — a drop-in replacement with no third-party iOS dependencies.

## What changed

The upstream plugin bundles SDWebImage, Mantle, and libwebp on iOS just for WebP support. This fork strips all of that:

| | Upstream | Lite |
|---|---|---|
| iOS deps | SDWebImage, SDWebImageWebPCoder, Mantle | **none** |
| Android deps | exifinterface, heifwriter, commons-io | exifinterface, heifwriter |
| iOS WebP decoding | via SDWebImage | native (iOS 14+) |
| iOS WebP encoding | via SDWebImage | not supported |
| HEIC/HEIF | yes | yes |
| JPEG/PNG | yes | yes |
| keepExif (iOS) | via Mantle/SYMetadata | native ImageIO (CGImageSource/CGImageDestination) |
| keepExif (Android) | via ExifInterface | via ExifInterface (removed commons-io) |
| iOS packaging | CocoaPods | SPM (stub podspec for compat) |
| AGP | 8+ (Groovy) | 9+ only (Kotlin DSL) |
| iOS deployment target | 9.0 | 15.0 |
| Android minSdk | 21 | 24 |
| Dart/Flutter | >=2.12/>=2.0 | ^3.11/^3.41 |

## Usage

Published on [pub.dev](https://pub.dev/packages/flutter_image_compress_common_lite). Add as a direct dependency alongside the main package:

```yaml
dependencies:
  flutter_image_compress: ^2.4.0
  flutter_image_compress_common_lite: ^1.0.7
```

Flutter's federated plugin resolution picks this over the default `flutter_image_compress_common` for Android and iOS. The Dart API stays unchanged.

**Note:** SPM is used for the actual iOS build. A stub podspec is included for compatibility with Flutter's CocoaPods-based build pipeline.

## License

Same as upstream: MIT
