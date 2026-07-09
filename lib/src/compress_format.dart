/// Output encoding for compressed images.
enum CompressFormat {
  /// JPEG — lossy, no alpha. Default.
  jpeg,

  /// PNG — lossless, with alpha.
  png,

  /// HEIC (HEVC in HEIF container) — lossy, with alpha. Android needs API 28+.
  heic,

  /// WebP — lossy, with alpha. Encoding is Android-only.
  webp;
}
