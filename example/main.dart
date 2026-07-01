// Minimal usage example for flutter_image_compress_lite.
//
// The plugin exposes static methods on `FlutterImageCompress`. All four
// entry points accept the same tuning parameters (`minWidth`, `minHeight`,
// `quality`, `rotate`, `format`, `keepExif`, `autoCorrectionAngle`);
// only the input/output shape differs.

import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_image_compress_lite/flutter_image_compress_lite.dart';

/// Compresses [sourcePath] into [targetPath] as JPEG and returns the
/// resulting file.
Future<XFile> compressFile(String sourcePath, String targetPath) => FlutterImageCompress.compressAndGetFile(
  sourcePath,
  targetPath,
  quality: 88,
  minWidth: 1920,
  minHeight: 1080,
  format: .jpeg,
  keepExif: true,
);

/// Re-encodes raw image [bytes] as HEIC (Android API 28+ / iOS 15+) and
/// returns the compressed bytes.
Future<Uint8List> compressBytesToHeic(Uint8List bytes) => FlutterImageCompress.compressWithList(
  bytes,
  quality: 80,
  format: .heic,
);

/// Loads a bundled asset and returns it compressed as WebP. WebP encoding
/// is Android-only — on iOS this throws [UnsupportedError].
Future<Uint8List> compressAssetToWebp(String assetName) => FlutterImageCompress.compressAssetImage(
  assetName,
  quality: 75,
  format: .webp,
);

void main() async {
  final tmp = Directory.systemTemp.path;
  final out = await compressFile('$tmp/input.jpg', '$tmp/output.jpg');
  final size = await File(out.path).length();
  // ignore: avoid_print
  print('Compressed to ${out.path} (${size ~/ 1024} KiB)');
}
