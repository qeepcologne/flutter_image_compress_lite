## 1.0.7

Forked from [flutter_image_compress_common 1.0.6](https://pub.dev/packages/flutter_image_compress_common).
Legacy-free: no CocoaPods, no Groovy, no AGP <9, no third-party iOS deps.

- **BREAKING**: Remove WebP encoding support on iOS (decoding works natively on iOS 14+)
- **BREAKING**: Remove CocoaPods support — SPM only
- **iOS**: Remove SDWebImage, SDWebImageWebPCoder, Mantle dependencies
- **iOS**: Remove SYPictureMetadata — keepExif reimplemented with native ImageIO
- **iOS**: Add Swift Package Manager support (Package.swift)
- **iOS**: Bump deployment target to 15.0
- **Android**: Remove unused commons-io dependency
- **Android**: Migrate build.gradle to Kotlin DSL, require AGP 9+
- **Android**: Bump minSdk to 24, exifinterface 1.3.3 → 1.4.2, heifwriter 1.0.0 → 1.1.0
- **Dart/Flutter**: Require Dart ^3.11.0, Flutter ^3.41.0
