import 'dart:io';

import 'package:flutter/services.dart';

import 'compress_format.dart';

class FlutterImageCompressValidator {
  FlutterImageCompressValidator(this.channel);

  final MethodChannel channel;

  /// Validates the *encoding* target — input formats are auto-detected by
  /// the native decoder and never need a check. Plugin only registers on
  /// Android+iOS, so the remaining runtime constraints are:
  ///   - HEIC encoding requires Android API 28+ (Android 9)
  ///   - WebP encoding is not supported on iOS (decoding works on iOS 14+)
  /// Throws [UnsupportedError] when the encoding is unsupported.
  Future<void> checkSupportPlatform(CompressFormat format) async {
    if (format == .webp && Platform.isIOS) {
      throw UnsupportedError('WebP encoding is not supported on iOS');
    }
    if (format == .heic && Platform.isAndroid) {
      final int version = await channel.invokeMethod('getSystemVersion');
      if (version < 28) {
        throw UnsupportedError('HEIC encoding requires Android API 28+ (Android 9)');
      }
    }
  }
}
