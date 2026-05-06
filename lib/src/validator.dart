import 'dart:io';

import 'package:flutter/services.dart';

import 'compress_format.dart';

class FlutterImageCompressValidator {
  FlutterImageCompressValidator(this.channel);

  final MethodChannel channel;

  bool ignoreCheckExtName = false;
  bool ignoreCheckSupportPlatform = false;

  void checkFileNameAndFormat(String name, CompressFormat format) {
    if (ignoreCheckExtName) return;
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

  /// Plugin only registers on Android+iOS. HEIC and WebP both work on iOS
  /// (15+) without checks; the only remaining runtime constraint is HEIC
  /// encoding requires Android API 28+.
  Future<bool> checkSupportPlatform(CompressFormat format) async {
    if (ignoreCheckSupportPlatform) return true;
    if (format == .heic && Platform.isAndroid) {
      final int version = await channel.invokeMethod('getSystemVersion');
      if (version < 28) {
        throw UnsupportedError('HEIC requires Android API 28+');
      }
    }
    return true;
  }
}
