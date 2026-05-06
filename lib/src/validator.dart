part of '../flutter_image_compress_lite.dart';

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

  Future<bool> checkSupportPlatform(CompressFormat format) async {
    if (ignoreCheckSupportPlatform) return true;
    if (format == .heic) {
      if (Platform.isIOS) {
        final String version =
            await channel.invokeMethod('getSystemVersion');
        final result = int.parse(version.split('.')[0]) >= 11;
        if (!result) throw UnsupportedError('HEIC requires iOS 11.0+');
        return result;
      } else if (Platform.isAndroid) {
        final int version = await channel.invokeMethod('getSystemVersion');
        if (version < 28) {
          throw UnsupportedError('HEIC requires Android API 28+');
        }
        return true;
      } else {
        throw UnsupportedError('HEIC only supported on Android and iOS.');
      }
    } else if (format == .webp) {
      if (Platform.isAndroid || Platform.isIOS) return true;
      throw UnsupportedError('WebP only supported on Android and iOS.');
    }
    return true;
  }
}
