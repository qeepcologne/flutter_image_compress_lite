# TODO

Findings from cleanup audit (2026-05-06) — items deferred for later.

## Android

### Cleanup
- **Dedupe EXIF rotation read in `CompressFileHandler.handle`** — currently reads `File(filePath).readBytes()` then calls `Exif.getRotationDegrees(bytes)`, while `handleGetFile` uses the `File` overload directly. Switch `handle()` to the `File` overload to avoid loading the whole file into memory just to read EXIF.
- **Move `FormatRegister` init out of plugin `init { }`** — `ImageCompressPlugin.init` populates the singleton on every plugin instance construction. If Flutter creates a second instance (engine reuse), duplicate `append`s occur. Move into `FormatRegister` itself (lazy or `init { }` on the object).
- **Style nit verification** — scan for any remaining `name:Type` (no-space) patterns.

### Correctness
- **`RGB_565` drops alpha** — `CommonHandler` and `HeifHandler` hardcode `Bitmap.Config.RGB_565`. Fine for JPEG (no alpha), wrong for PNG/WebP/HEIC where transparency is silently lost. Switch to `ARGB_8888` for alpha-supporting formats.
- **Unknown format silently returns `null`** — Android handlers `reply(null)` on unknown format index. The Dart enum can't produce an unknown index today, but if wire format ever desyncs it would fail invisibly. Consider `result.error(...)` with a code instead.

### Modernization
- **Replace thread pool with coroutines** — `ResultHandler` uses a singleton `Executors.newFixedThreadPool(8)` that's never shut down on `onDetachedFromEngine` (leak). Migrate to a `CoroutineScope` cancelled in `onDetached`. Removes the static `Handler(Looper.getMainLooper())` (use `Dispatchers.Main`).
- **Drop `androidx.heifwriter`** — `1.1.0` is in maintenance mode and forces the `tools:overrideLibrary` workaround in `AndroidManifest.xml`. Forward path: `MediaMuxer` + `MediaCodec` HEIC encoder (API 28+, already the gate). Larger lift.

## iOS

### Cleanup
- **Collapse `compressWithUIImage:` / `compressDataWithUIImage:`** — two near-identical methods in `CompressHandler`, only differ by logging. `handleMethodCall` uses one, `handleCompressFileToFile` uses the other → file→bytes logs but file→file doesn't. Pick one.
- **Style cleanup** — `CompressListHandler.h` doesn't `#import <Flutter/Flutter.h>` (relies on caller order); empty `{ }` after `@implementation` in `CompressFileHandler.m` and `CompressHandler.m`; old `// Created by cjl on 2018/9/8.` attribution comments on every file.

### Correctness
- **No file/decode validation in `CompressFileHandler`** — `dataWithContentsOfURL:` and `imageWithData:` are not checked for nil. Missing/corrupt file → silent nil propagation. Return a `FlutterError` with a clear code.
- **Misleading `serial_queue` name** — `ImageCompressPlugin.m` declares `static dispatch_queue_t serial_queue = dispatch_get_global_queue(0, 0)` — that's the *concurrent* global queue. Either rename or actually create a serial queue. Replace legacy priority `0` with a QOS class.

### Modernization
- **Replace deprecated `UIGraphicsBeginImageContext`** — used in both scale and rotate paths in `UIImage+scale.m`. Deprecated since iOS 10. Switch to `UIGraphicsImageRenderer`.
- **Simplify `imageRotatedByDegrees:` with `CGRectApplyAffineTransform`** — current code creates a `UIView` just to compute a rotated bounding box. Pure CG math does it without touching UIKit (and without the implicit main-thread requirement).
- **Rewrite plugin in Swift** — drops `__bridge` casts, manual `CFRelease` lifecycle, public-headers folder, and per-file `.h/.m` split. Larger lift.
