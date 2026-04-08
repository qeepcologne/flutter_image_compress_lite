## 2.0.0

Merged `flutter_image_compress` + `flutter_image_compress_common` into a single standalone package.
No federated plugin architecture, no transitive dependencies with podspecs, no CocoaPods required.

- **BREAKING**: New package name `flutter_image_compress_lite` — change import
- **BREAKING**: Remove WebP encoding on iOS (decoding works natively on iOS 14+)
- **BREAKING**: Require Dart ^3.11.0, Flutter >=3.41.0, iOS 15.0+, Android minSdk 24, AGP 9+
- **iOS**: SPM only, zero third-party deps
- **iOS**: keepExif via native ImageIO (no Mantle)
- **Android**: Kotlin DSL, removed commons-io, bumped exifinterface 1.4.2, heifwriter 1.1.0

