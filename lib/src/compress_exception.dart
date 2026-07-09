/// Thrown by the Dart side for argument-level failures (e.g. missing source
/// file, empty input). Native decode/encode failures arrive as a
/// `PlatformException` instead.
class CompressException implements Exception {
  /// Creates a [CompressException] with the given human-readable [message].
  CompressException(this.message);

  /// Human-readable description of what went wrong.
  final String message;

  @override
  String toString() => 'CompressException: $message';
}
