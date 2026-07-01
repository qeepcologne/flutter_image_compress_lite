// Flutter's engine types (FlutterMethodCall, FlutterResult, …) are not Sendable-audited.
// @preconcurrency downgrades the resulting Sendable diagnostics to warnings; this is the
// irreducible cost of bridging the (non-Sendable) method channel into strict-concurrency code.
@preconcurrency import Flutter
import UIKit

@objc(ImageCompressPlugin)
public final class ImageCompressPlugin: NSObject, FlutterPlugin {

    // A mutable global is a data race under strict concurrency. The honest fix is a Mutex
    // (Synchronization), but that is iOS 18+ and we still support iOS 15 — so this debug-only
    // log flag keeps the same unsynchronized behavior it had as a plain `static var`.
    public nonisolated(unsafe) static var showLog: Bool = false

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(
            name: "flutter_image_compress",
            binaryMessenger: registrar.messenger()
        )
        registrar.addMethodCallDelegate(ImageCompressPlugin(), channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        if call.method == "showLog" {
            Self.showLog = (call.arguments as? Bool) ?? false
            result(1)
            return
        }

        // Touch the non-Sendable Flutter types here, on the calling thread, and parse everything
        // we need into a Sendable `Request`. Nothing from Flutter crosses the Task boundary.
        let request: Request
        do {
            request = try Request(method: call.method, arguments: call.arguments)
        } catch {
            result(FlutterError(code: "BAD_ARGS", message: nil, details: nil))
            return
        }

        // `result` is a non-Sendable closure; this is the one capture we knowingly allow across
        // the boundary (the work below runs off-actor and calls back exactly once).
        nonisolated(unsafe) let deliver = result
        Task {
            switch await Self.run(request) {
            case .bytes(let data):
                deliver(FlutterStandardTypedData(bytes: data))
            case .path(let path):
                deliver(path)
            case .failure(let code, let message):
                deliver(FlutterError(code: code, message: message, details: nil))
            }
        }
    }

    // @concurrent (Swift 6.2) keeps this CPU-heavy work off the caller's executor — the typed
    // replacement for the old `DispatchQueue.global(qos: .userInitiated).async { … }`.
    @concurrent
    private static func run(_ request: Request) async -> Outcome {
        let data: Data
        let applyExif: (Data) -> Data

        switch request.source {
        case .bytes(let bytes):
            data = bytes
            applyExif = { ExifKeeper.applyExif(fromData: bytes, to: $0) }
        case .file(let path):
            let url = URL(fileURLWithPath: path)
            guard let read = try? Data(contentsOf: url) else {
                return .failure(code: "FILE_NOT_FOUND", message: "could not read \(path)")
            }
            data = read
            applyExif = { ExifKeeper.applyExif(fromURL: url, to: $0) }
        }

        guard let image = UIImage(data: data) else {
            return .failure(code: "BAD_IMAGE", message: "could not decode image")
        }
        guard let compressed = Compressor.encode(image: image, params: request.params) else {
            // Compressor.encode returns nil only on edge cases (missing cgImage, encoder
            // failure) — surface them as COMPRESS_ERROR so the non-nullable Dart return types
            // hold, instead of silently delivering nil to the caller's Future.
            return .failure(code: "COMPRESS_ERROR", message: "encoder returned no data")
        }
        let output = request.params.keepExif ? applyExif(compressed) : compressed

        guard let target = request.targetPath else {
            return .bytes(output)
        }
        do {
            try output.write(to: URL(fileURLWithPath: target), options: .atomic)
            return .path(target)
        } catch {
            return .failure(code: "WRITE_FAILED", message: "\(error)")
        }
    }
}

// MARK: - Wire format

/// Order must match the Dart `CompressFormat` enum so `rawValue` is the wire value.
enum CompressFormat: Int, Sendable {
    case jpeg = 0
    case png = 1
    case heic = 2
    case webp = 3
}

struct CompressParams: Sendable {
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

// MARK: - Sendable request / outcome (the Task boundary)

/// A fully-parsed, Sendable description of one compress call — built on the calling thread so the
/// async worker never has to touch FlutterMethodCall.
struct Request: Sendable {
    enum Source: Sendable {
        case bytes(Data)
        case file(path: String)
    }

    let source: Source
    let targetPath: String?   // set only for compressWithFileAndGetFile
    let params: CompressParams

    enum ParseError: Error { case badArgs }

    init(method: String, arguments: Any?) throws {
        guard let args = arguments as? [Any] else { throw ParseError.badArgs }

        switch method {
        case "compressWithList":
            guard args.count >= 8,
                  let typed = args[0] as? FlutterStandardTypedData,
                  let formatIndex = (args[6] as? NSNumber)?.intValue,
                  let format = CompressFormat(rawValue: formatIndex)
            else { throw ParseError.badArgs }
            self.source = .bytes(typed.data)
            self.targetPath = nil
            self.params = CompressParams(args: args, format: format, keepExifIndex: 7)

        case "compressWithFile":
            guard args.count >= 8,
                  let path = args[0] as? String,
                  let formatIndex = (args[6] as? NSNumber)?.intValue,
                  let format = CompressFormat(rawValue: formatIndex)
            else { throw ParseError.badArgs }
            self.source = .file(path: path)
            self.targetPath = nil
            self.params = CompressParams(args: args, format: format, keepExifIndex: 7)

        case "compressWithFileAndGetFile":
            guard args.count >= 9,
                  let path = args[0] as? String,
                  let target = args[4] as? String,
                  let formatIndex = (args[7] as? NSNumber)?.intValue,
                  let format = CompressFormat(rawValue: formatIndex)
            else { throw ParseError.badArgs }
            self.source = .file(path: path)
            self.targetPath = target
            self.params = CompressParams(args: args, format: format, keepExifIndex: 8, rotateIndex: 5)

        default:
            throw ParseError.badArgs
        }
    }
}

enum Outcome: Sendable {
    case bytes(Data)
    case path(String)
    case failure(code: String, message: String?)
}
