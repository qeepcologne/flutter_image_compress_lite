import 'dart:io';
import 'dart:typed_data' as typed_data;

import 'package:cross_file/cross_file.dart';
import 'package:flutter/painting.dart';
import 'package:flutter/services.dart';

import 'src/compress_error.dart';
import 'src/compress_format.dart';
import 'src/validator.dart';

export 'package:cross_file/cross_file.dart';

export 'src/compress_error.dart';
export 'src/compress_format.dart';

/// Image Compress plugin.
///
/// Compress images using native platform APIs (iOS/Android).
/// Supports JPEG, PNG, HEIC output. WebP encoding only on Android.
/// WebP decoding works natively on iOS 14+.
class FlutterImageCompress {
  static const _channel = MethodChannel('flutter_image_compress');

  static final FlutterImageCompressValidator _validator = .new(_channel);


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
    int inSampleSize = _Defaults.inSampleSize,
    bool autoCorrectionAngle = _Defaults.autoCorrectionAngle,
    CompressFormat format = _Defaults.format,
    bool keepExif = _Defaults.keepExif,
  }) async {
    if (image.isEmpty) {
      throw CompressError('The image is empty.');
    }
    await _validator.checkSupportPlatform(format);
    final result = await _channel.invokeMethod('compressWithList', [
      image,
      minWidth,
      minHeight,
      quality,
      rotate,
      autoCorrectionAngle,
      format.nativeValue,
      keepExif,
      inSampleSize,
    ]);
    return result;
  }

  /// Compress file of [path] to [Uint8List].
  static Future<typed_data.Uint8List?> compressWithFile(
    String path, {
    int minWidth = _Defaults.minWidth,
    int minHeight = _Defaults.minHeight,
    int inSampleSize = _Defaults.inSampleSize,
    int quality = _Defaults.quality,
    int rotate = _Defaults.rotate,
    bool autoCorrectionAngle = _Defaults.autoCorrectionAngle,
    CompressFormat format = _Defaults.format,
    bool keepExif = _Defaults.keepExif,
  }) async {
    if (!await File(path).exists()) {
      throw CompressError('Image file does not exist in $path.');
    }
    await _validator.checkSupportPlatform(format);
    final result = await _channel.invokeMethod('compressWithFile', [
      path,
      minWidth,
      minHeight,
      quality,
      rotate,
      autoCorrectionAngle,
      format.nativeValue,
      keepExif,
      inSampleSize,
    ]);
    return result;
  }

  /// Compress file at [path] and write to [targetPath].
  static Future<XFile?> compressAndGetFile(
    String path,
    String targetPath, {
    int minWidth = _Defaults.minWidth,
    int minHeight = _Defaults.minHeight,
    int inSampleSize = _Defaults.inSampleSize,
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
    _validator.checkFileNameAndFormat(targetPath, format);
    await _validator.checkSupportPlatform(format);
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
        format.nativeValue,
        keepExif,
        inSampleSize,
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

class _Defaults {
  static const int minWidth = 1920;
  static const int minHeight = 1080;
  static const int quality = 95;
  static const int rotate = 0;
  static const int inSampleSize = 1;
  static const bool autoCorrectionAngle = true;
  static const CompressFormat format = .jpeg;
  static const bool keepExif = false;
}
