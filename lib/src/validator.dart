import 'dart:io';

import 'package:flutter/services.dart';

import 'compress_format.dart';

class FlutterImageCompressValidator {
  FlutterImageCompressValidator(this.channel);

  final MethodChannel channel;

  void checkFileNameAndFormat(String name, CompressFormat format) {
    name = name.toLowerCase();
    switch (format) {
      case .jpeg:
        assert(name.endsWith('.jpg') || name.endsWith('.jpeg'),
            'The jpeg format name must end with jpg or jpeg.');
      case .png:
        assert(name.endsWith('.png'), 'The png format name must end with png.');
      case .heic:
        assert(
            name.endsWith('.heic'), 'The heic format name must end with heic.');
      case .webp:
        assert(
            name.endsWith('.webp'), 'The webp format name must end with webp.');
    }
  }

  /// Validates the *encoding* target — input formats are auto-detected by
  /// the native decoder and never need a check. Plugin only registers on
  /// Android+iOS, so the remaining runtime constraints are:
  ///   - HEIC encoding requires Android API 28+
  ///   - WebP encoding is not supported on iOS (decoding works on iOS 14+)
  /// Throws [UnsupportedError] when the encoding is unsupported.
  Future<void> checkSupportPlatform(CompressFormat format) async {
    if (format == .webp && Platform.isIOS) {
      throw UnsupportedError('WebP encoding is not supported on iOS');
    }
    if (format == .heic && Platform.isAndroid) {
      final int version = await channel.invokeMethod('getSystemVersion');
      if (version < 28) {
        throw UnsupportedError('HEIC requires Android API 28+');
      }
    }
  }
}
