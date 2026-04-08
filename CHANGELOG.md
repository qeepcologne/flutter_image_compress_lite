## 1.0.7 (lite fork)

- **BREAKING**: Remove WebP encoding support on iOS (decoding works natively on iOS 14+)
- **iOS**: Remove SDWebImage, SDWebImageWebPCoder, Mantle dependencies
- **iOS**: Remove SYPictureMetadata — keepExif reimplemented with native ImageIO
- **iOS**: Add Swift Package Manager support (Package.swift)
- **iOS**: Bump deployment target to 14.0
- **Android**: Remove unused commons-io dependency
- **Android**: Migrate build.gradle to Kotlin DSL with AGP 8+9 compat

## 1.0.6

- **DEPS**: Bump `compileSdk` to `34`.

## 1.0.5

 - **DOCS**: The first version for OpenHarmony.

## 1.0.4

- **DEPS**: Bump KGP (Kotlin Gradle Plugin) to `1.8.20`.
- **DEPS**: Bump Java source compatibility and the JVM target to `11.`

## 1.0.3

 - **DOCS**: Update README.

## 1.0.2

 - **DOCS**: Update changelog.

## 1.0.1

- Change sdk constraint to `>=2.12.0 <4.0.0`.

## 1.0.0

- The first version for migrate to platform interface.
