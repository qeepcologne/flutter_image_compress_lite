/// Output encoding for compressed images. WebP encoding is Android-only;
/// HEIC encoding requires Android API 28+ (iOS supports it on all
/// supported versions).
enum CompressFormat {
  /// JPEG — lossy, no alpha. Default.
  jpeg,

  /// PNG — lossless, with alpha.
  png,

  /// HEIC (HEVC in HEIF container) — lossy, with alpha. Android needs API 28+.
  heic,

  /// WebP — lossy, with alpha. Encoding is Android-only.
  webp;

  /// Wire value sent over the platform channel to the native side.
  int get nativeValue => index;
}
