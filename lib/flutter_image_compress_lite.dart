/// Minimal, legacy-free image compression for Flutter (Android + iOS).
///
/// Drop-in replacement for `flutter_image_compress` with no CocoaPods, SPM
/// only on iOS, and no federated sub-packages. See [FlutterImageCompress]
/// for the entry points.
library;

import 'dart:io';
import 'dart:typed_data' as typed_data;

import 'package:cross_file/cross_file.dart';
import 'package:flutter/painting.dart';
import 'package:flutter/services.dart';

import 'src/compress_error.dart';
import 'src/compress_format.dart';

export 'package:cross_file/cross_file.dart';

export 'src/compress_error.dart';
export 'src/compress_format.dart';

/// Image Compress plugin.
///
/// Compress images using native platform APIs (iOS/Android).
/// Supports JPEG, PNG, HEIC output. WebP encoding only on Android.
/// WebP decoding works natively on iOS 14+.
///
/// **About `minWidth` / `minHeight`:** these are *lower bounds* on the
/// output dimensions, not target sizes. The image is downscaled with its
/// aspect ratio preserved so that both axes end up ≥ the requested minimum
/// — one axis lands exactly on its minimum, the other exceeds it. Images
/// already smaller than the minimum on both axes are returned at their
/// original size (never upscaled).
class FlutterImageCompress {
  FlutterImageCompress._();

  static const _channel = MethodChannel('flutter_image_compress');

  /// Enables verbose logging from the native side (Android `Log.i`,
  /// iOS `NSLog`). Off by default. Useful for debugging compression
  /// pipelines; leave disabled in release builds.
  static set showNativeLog(bool value) {
    _channel.invokeMethod('showLog', value);
  }

  /// Compress image from [Uint8List] to [Uint8List].
  static Future<typed_data.Uint8List> compressWithList(
    typed_data.Uint8List image, {
    int minWidth = _Defaults.minWidth,
    int minHeight = _Defaults.minHeight,
    int quality = _Defaults.quality,
    int rotate = _Defaults.rotate,
    bool autoCorrectionAngle = _Defaults.autoCorrectionAngle,
    CompressFormat format = _Defaults.format,
    bool keepExif = _Defaults.keepExif,
  }) async {
    if (image.isEmpty) {
      throw CompressError('The image is empty.');
    }
    await _checkSupportPlatform(format);
    final result = await _channel.invokeMethod('compressWithList', [
      image,
      minWidth,
      minHeight,
      quality,
      rotate,
      autoCorrectionAngle,
      format.index,
      keepExif,
    ]);
    return result;
  }

  /// Compress file of [path] to [Uint8List].
  static Future<typed_data.Uint8List?> compressWithFile(
    String path, {
    int minWidth = _Defaults.minWidth,
    int minHeight = _Defaults.minHeight,
    int quality = _Defaults.quality,
    int rotate = _Defaults.rotate,
    bool autoCorrectionAngle = _Defaults.autoCorrectionAngle,
    CompressFormat format = _Defaults.format,
    bool keepExif = _Defaults.keepExif,
  }) async {
    if (!await File(path).exists()) {
      throw CompressError('Image file does not exist in $path.');
    }
    await _checkSupportPlatform(format);
    final result = await _channel.invokeMethod('compressWithFile', [
      path,
      minWidth,
      minHeight,
      quality,
      rotate,
      autoCorrectionAngle,
      format.index,
      keepExif,
    ]);
    return result;
  }

  /// Compress file at [path] and write to [targetPath].
  static Future<XFile?> compressAndGetFile(
    String path,
    String targetPath, {
    int minWidth = _Defaults.minWidth,
    int minHeight = _Defaults.minHeight,
    int quality = _Defaults.quality,
    int rotate = _Defaults.rotate,
    bool autoCorrectionAngle = _Defaults.autoCorrectionAngle,
    CompressFormat format = _Defaults.format,
    bool keepExif = _Defaults.keepExif,
  }) async {
    if (!await File(path).exists()) {
      throw CompressError('Image file does not exist in $path.');
    }
    if (path == targetPath) {
      throw CompressError('Target path and source path cannot be the same.');
    }
    await _checkSupportPlatform(format);
    final String? result = await _channel.invokeMethod(
      'compressWithFileAndGetFile',
      [
        path,
        minWidth,
        minHeight,
        quality,
        targetPath,
        rotate,
        autoCorrectionAngle,
        format.index,
        keepExif,
      ],
    );
    if (result == null) {
      return null;
    }
    return XFile(result);
  }

  /// Compress image from asset.
  static Future<typed_data.Uint8List?> compressAssetImage(
    String assetName, {
    int minWidth = _Defaults.minWidth,
    int minHeight = _Defaults.minHeight,
    int quality = _Defaults.quality,
    int rotate = _Defaults.rotate,
    bool autoCorrectionAngle = _Defaults.autoCorrectionAngle,
    CompressFormat format = _Defaults.format,
    bool keepExif = _Defaults.keepExif,
  }) async {
    final img = AssetImage(assetName);
    const config = ImageConfiguration();
    final AssetBundleImageKey key = await img.obtainKey(config);
    final ByteData data = await key.bundle.load(key.name);
    final uint8List = data.buffer.asUint8List();
    if (uint8List.isEmpty) {
      return null;
    }
    return compressWithList(
      uint8List,
      minHeight: minHeight,
      minWidth: minWidth,
      quality: quality,
      rotate: rotate,
      autoCorrectionAngle: autoCorrectionAngle,
      format: format,
      keepExif: keepExif,
    );
  }
}

/// Validates the *encoding* target — input formats are auto-detected by the
/// native decoder and never need a check. Plugin only registers on
/// Android+iOS, so the remaining runtime constraints are:
///   - HEIC encoding requires Android API 28+ (Android 9)
///   - WebP encoding is not supported on iOS (decoding works on iOS 14+)
Future<void> _checkSupportPlatform(CompressFormat format) async {
  if (format == .webp && Platform.isIOS) {
    throw UnsupportedError('WebP encoding is not supported on iOS');
  }
  if (format == .heic && Platform.isAndroid) {
    final int version = await FlutterImageCompress._channel.invokeMethod('getSystemVersion');
    if (version < 28) {
      throw UnsupportedError('HEIC encoding requires Android API 28+ (Android 9)');
    }
  }
}

class _Defaults {
  static const int minWidth = 1920;
  static const int minHeight = 1080;
  static const int quality = 95;
  static const int rotate = 0;
  static const bool autoCorrectionAngle = true;
  static const CompressFormat format = .jpeg;
  static const bool keepExif = false;
}
