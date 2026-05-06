// ignore_for_file: require_trailing_commas

import 'dart:async';
import 'dart:io';
import 'dart:typed_data' as typed_data;

import 'package:cross_file/cross_file.dart';
import 'package:flutter/material.dart';
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
  static Future<typed_data.Uint8List> compressWithList(typed_data.Uint8List image, {
    int minWidth = 1920,
    int minHeight = 1080,
    int quality = 95,
    int rotate = 0,
    int inSampleSize = 1,
    bool autoCorrectionAngle = true,
    CompressFormat format = .jpeg,
    bool keepExif = false,
  }) async {
    if (image.isEmpty) {
      throw CompressError('The image is empty.');
    }
    final support = await _validator.checkSupportPlatform(format);
    if (!support) {
      throw UnsupportedError('The image type $format is not supported.');
    }
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
  static Future<typed_data.Uint8List?> compressWithFile(String path, {
    int minWidth = 1920,
    int minHeight = 1080,
    int inSampleSize = 1,
    int quality = 95,
    int rotate = 0,
    bool autoCorrectionAngle = true,
    CompressFormat format = .jpeg,
    bool keepExif = false,
    int numberOfRetries = 5,
  }) async {
    if (numberOfRetries <= 0) {
      throw CompressError("numberOfRetries can't be null or less than 0");
    }
    if (!File(path).existsSync()) {
      throw CompressError('Image file does not exist in $path.');
    }
    final support = await _validator.checkSupportPlatform(format);
    if (!support) {
      return null;
    }
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
      numberOfRetries
    ]);
    return result;
  }

  /// Compress file at [path] and write to [targetPath].
  static Future<XFile?> compressAndGetFile(String path,
      String targetPath, {
        int minWidth = 1920,
        int minHeight = 1080,
        int inSampleSize = 1,
        int quality = 95,
        int rotate = 0,
        bool autoCorrectionAngle = true,
        CompressFormat format = .jpeg,
        bool keepExif = false,
        int numberOfRetries = 5,
      }) async {
    if (numberOfRetries <= 0) {
      throw CompressError("numberOfRetries can't be null or less than 0");
    }
    if (!File(path).existsSync()) {
      throw CompressError('Image file does not exist in $path.');
    }
    if (path == targetPath) {
      throw CompressError('Target path and source path cannot be the same.');
    }
    _validator.checkFileNameAndFormat(targetPath, format);
    final support = await _validator.checkSupportPlatform(format);
    if (!support) {
      return null;
    }
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
        numberOfRetries,
      ],
    );
    if (result == null) {
      return null;
    }
    return XFile(result);
  }

  /// Compress image from asset.
  static Future<typed_data.Uint8List?> compressAssetImage(String assetName, {
    int minWidth = 1920,
    int minHeight = 1080,
    int quality = 95,
    int rotate = 0,
    bool autoCorrectionAngle = true,
    CompressFormat format = .jpeg,
    bool keepExif = false,
  }) async {
    final support = await _validator.checkSupportPlatform(format);
    if (!support) {
      return null;
    }
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
