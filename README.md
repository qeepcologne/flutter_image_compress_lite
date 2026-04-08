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
| SPM support | no | yes |
| CocoaPods | yes | yes |
| AGP 9 compat | no | yes (Kotlin DSL) |
| iOS deployment target | 9.0 | 14.0 |

## Usage

Use as a `dependency_override` for `flutter_image_compress_common` in your `pubspec.yaml`:

```yaml
dependency_overrides:
  flutter_image_compress_common:
    git:
      url: https://github.com/qeepcologne/flutter_image_compress_lite.git
```

The Dart API (`flutter_image_compress`) stays unchanged — this only replaces the native implementation.

## License

Same as upstream: MIT
