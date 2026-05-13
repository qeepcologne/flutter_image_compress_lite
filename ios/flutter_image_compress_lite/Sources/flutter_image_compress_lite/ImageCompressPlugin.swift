import Flutter
import UIKit

@objc(ImageCompressPlugin)
public final class ImageCompressPlugin: NSObject, FlutterPlugin {

    @objc public static var showLog: Bool = false

    private static let workQueue = DispatchQueue.global(qos: .userInitiated)

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(
            name: "flutter_image_compress",
            binaryMessenger: registrar.messenger()
        )
        let instance = ImageCompressPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        Self.workQueue.async {
            switch call.method {
            case "compressWithList":
                Self.compressList(call: call, result: result)
            case "compressWithFile":
                Self.compressFileToBytes(call: call, result: result)
            case "compressWithFileAndGetFile":
                Self.compressFileToFile(call: call, result: result)
            case "showLog":
                Self.showLog = (call.arguments as? Bool) ?? false
                result(1)
            default:
                result(FlutterMethodNotImplemented)
            }
        }
    }

    // MARK: - Method handlers

    private static func compressList(call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [Any], args.count >= 8,
              let typed = args[0] as? FlutterStandardTypedData,
              let formatIndex = (args[6] as? NSNumber)?.intValue,
              let format = CompressFormat(rawValue: formatIndex) else {
            result(error("BAD_ARGS"))
            return
        }
        let data = typed.data
        let params = CompressParams(args: args, format: format, keepExifIndex: 7)

        guard let image = UIImage(data: data) else {
            result(error("BAD_IMAGE", "could not decode image bytes"))
            return
        }
        guard let compressed = Compressor.encode(image: image, params: params) else {
            result(nil)
            return
        }
        let output = params.keepExif
            ? ExifKeeper.applyExif(fromData: data, to: compressed)
            : compressed
        result(FlutterStandardTypedData(bytes: output))
    }

    private static func compressFileToBytes(call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [Any], args.count >= 8,
              let path = args[0] as? String,
              let formatIndex = (args[6] as? NSNumber)?.intValue,
              let format = CompressFormat(rawValue: formatIndex) else {
            result(error("BAD_ARGS"))
            return
        }
        let params = CompressParams(args: args, format: format, keepExifIndex: 7)

        let url = URL(fileURLWithPath: path)
        guard let data = try? Data(contentsOf: url) else {
            result(error("FILE_NOT_FOUND", "could not read \(path)"))
            return
        }
        guard let image = UIImage(data: data) else {
            result(error("BAD_IMAGE", "could not decode image at \(path)"))
            return
        }
        guard let compressed = Compressor.encode(image: image, params: params) else {
            result(nil)
            return
        }
        let output = params.keepExif
            ? ExifKeeper.applyExif(fromURL: url, to: compressed)
            : compressed
        result(FlutterStandardTypedData(bytes: output))
    }

    private static func compressFileToFile(call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [Any], args.count >= 9,
              let path = args[0] as? String,
              let targetPath = args[4] as? String,
              let formatIndex = (args[7] as? NSNumber)?.intValue,
              let format = CompressFormat(rawValue: formatIndex) else {
            result(error("BAD_ARGS"))
            return
        }
        let params = CompressParams(
            args: args,
            format: format,
            keepExifIndex: 8,
            rotateIndex: 5
        )

        let url = URL(fileURLWithPath: path)
        guard let data = try? Data(contentsOf: url) else {
            result(error("FILE_NOT_FOUND", "could not read \(path)"))
            return
        }
        guard let image = UIImage(data: data) else {
            result(error("BAD_IMAGE", "could not decode image at \(path)"))
            return
        }
        guard let compressed = Compressor.encode(image: image, params: params) else {
            result(nil)
            return
        }
        let finalData = params.keepExif
            ? ExifKeeper.applyExif(fromURL: url, to: compressed)
            : compressed
        do {
            try finalData.write(to: URL(fileURLWithPath: targetPath), options: .atomic)
            result(targetPath)
        } catch {
            result(self.error("WRITE_FAILED", "\(error)"))
        }
    }

    private static func error(_ code: String, _ message: String? = nil) -> FlutterError {
        return FlutterError(code: code, message: message, details: nil)
    }
}

// MARK: - Wire format

/// Order must match the Dart `CompressFormat` enum so `rawValue` is the wire value.
enum CompressFormat: Int {
    case jpeg = 0
    case png = 1
    case heic = 2
    case webp = 3
}

struct CompressParams {
    let minWidth: Int
    let minHeight: Int
    let quality: Int
    let rotate: Int
    let format: CompressFormat
    let keepExif: Bool

    init(args: [Any], format: CompressFormat, keepExifIndex: Int, rotateIndex: Int = 4) {
        self.minWidth = (args[1] as? NSNumber)?.intValue ?? 0
        self.minHeight = (args[2] as? NSNumber)?.intValue ?? 0
        self.quality = (args[3] as? NSNumber)?.intValue ?? 95
        self.rotate = (args[rotateIndex] as? NSNumber)?.intValue ?? 0
        self.format = format
        self.keepExif = (args[keepExifIndex] as? NSNumber)?.boolValue ?? false
    }
}
