/// Thrown by the Dart side for argument-level failures (e.g. missing source
/// file, empty input). Native decode/encode failures arrive as a
/// `PlatformException` instead.
class CompressError implements Exception {
  /// Creates a [CompressError] with the given human-readable [message].
  CompressError(this.message);

  /// Human-readable description of what went wrong.
  final String message;

  @override
  String toString() => 'CompressError: $message';
}
