# TODO

Deferred item from upstream `fluttercandies/flutter_image_compress` issue audit. Requires a physical device or real test image and cannot be verified from the plugin's automated toolchain — pick it up when a bug report surfaces or when device time is available.

## HEIC skew when EXIF rotation applied (Android)

**Upstream:** [#317](https://github.com/fluttercandies/flutter_image_compress/issues/317)

**Repro:** portrait JPEG (EXIF orientation=6) → `compressWithFile(format: .heic, autoCorrectionAngle: true)` → open in a HEIC-aware viewer.

**Expected:** output has same visible orientation as input.

**Suspect area:** `android/src/main/kotlin/com/fluttercandies/flutter_image_compress/Compressor.kt` — `encodeHeic()` uses `bitmap.width`/`bitmap.height`, but the (w, h) target passed into `compress()` may already have been axis-swapped by `rotatedTarget()` in `ImageCompressPlugin.kt`. Trace the dims through when `exifRotate == 90`.
