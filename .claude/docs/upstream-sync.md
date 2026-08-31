# Upstream sync (fluttercandies/flutter_image_compress)

This package is a **rewrite**, not a fork: Kotlin/Swift written from scratch against the same method channel.
So upstream changes can never be merged — each one has to be read as a diff and mapped onto our equivalent code.

## Method

- Sweep **commit diffs, never PR titles**. A PR whose headline describes something we already do can still carry a
  second, unrelated behaviour change in its diff. That is exactly how upstream #335 was missed in the 2.5.0 catch-up
  (2.7.2): its title is the `UIGraphicsImageRenderer` migration, which we had done since our first commit — the
  `format.preferredRange = .standard` pin sitting two lines below only surfaced 5 weeks later, via #407.
- The verdict "we already solve this differently" must be verified line by line against our source. Our rewrite can
  silently lack a detail that upstream's version has.
- Skip nothing on the grounds of "internal": upstream's structure differs everywhere, so every diff looks internal.

## Synced through

Upstream `3fec79e` (2026-07-25) = `flutter_image_compress` 2.5.1 / `_common` 1.1.1. Nothing has landed upstream on
`packages/flutter_image_compress_common` since. Full sweep of the 2026-07-12…07-25 window done for our 2.9.3.

| upstream | ours |
|---|---|
| #335 renderer + `preferredRange` | **taken in 2.9.3** — missed in the 2.7.2 catch-up |
| #358 HEIC nil colorSpace → sRGB | **taken in 2.9.3** (we had `CGColorSpaceCreateDeviceRGB()`). Of the silent-write-failure half we have the behaviour — `heifRepresentation` returns nil, which the plugin maps to `COMPRESS_ERROR` — but deliberately not the failure *reason*: the data-returning API has no error out-param, and the file-based `writeHEIFRepresentation` that does would reintroduce the tmp file of #377 for a debug log. 2.9.3 logs that the encode failed, not why |
| #407 Android decode to sRGB (open upstream) | **shipped first in 2.9.3**, one decode-options helper instead of three call sites |
| #366 drop AssetsLibrary, #392 drop SYPictureMetadata/Mantle | n/a — we never had those deps |
| #323 SPM migration, #390 built-in Kotlin, #401 Gradle 9 Kotlin modes, #340 commons-io | n/a — SPM-only and AGP-9-only from the start, no commons-io |
| #370 rotated bbox without UIKit | have — `CGRect.applying(…).integral` since 2.7.1 |
| #369 keepExif nil metadata, #391 CGImage passthrough | have — `ExifKeeper.apply` returns the original bytes on any ImageIO failure and uses `CGImageDestinationAddImageFromSource` |
| #374 unreadable / non-image input, #372 unreadable input on Android, #397 errors instead of null | have, and stricter — both platforms map every failure to a wire error code (`FILE_NOT_FOUND`/`BAD_IMAGE`/`COMPRESS_ERROR`/`WRITE_FAILED`), Android also catches `OutOfMemoryError` |
| #377 / #380 delete HEIC tmp file | have — `encodeToTempFile` deletes in `finally`; iOS writes no tmp file at all |
| #376 full EXIF + tmp leak, #403 deprecated ISO tag | have — we copy every `TAG_*` minus a skip list (a superset of upstream's allow-list), so a renamed constant can't affect us |
| #373 ARGB_8888, #375/#389 mkdirs parent, #382/#387/#388 recycle bitmaps, #394/#395 EXIF on PNG/WebP + `Log.w` | have (2.7.1 / 2.7.2) |
| #381 ISO BMFF ftyp mime sniffer | n/a — upstream sniffs only to route WebP to SDWebImage; `UIImage(data:)` decodes HEIC/AVIF natively for us |
| #386 reject `compressAndGetFile` source == target | have — `ArgumentError` in the Dart layer |
