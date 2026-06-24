import UIKit
import CoreImage
import os

private let log = Logger(
    subsystem: "com.qeepcologne.flutter_image_compress_lite",
    category: "compress"
)

enum Compressor {
    static func encode(image: UIImage, params: CompressParams) -> Data? {
        if ImageCompressPlugin.showLog {
            log.info("width = \(image.size.width)")
            log.info("height = \(image.size.height)")
            log.info("minWidth = \(params.minWidth)")
            log.info("minHeight = \(params.minHeight)")
            log.info("format = \(params.format.rawValue)")
        }
        var img = image.scaled(toMinWidth: CGFloat(params.minWidth),
                               minHeight: CGFloat(params.minHeight))
        if params.rotate % 360 != 0 {
            img = img.rotated(byDegrees: CGFloat(params.rotate))
        }
        return data(from: img, quality: params.quality, format: params.format)
    }

    private static func data(from image: UIImage, quality: Int, format: CompressFormat) -> Data? {
        let q = CGFloat(quality) / 100.0
        switch format {
        case .jpeg:
            return image.jpegData(compressionQuality: q)
        case .png:
            return image.pngData()
        case .heic:
            return heifData(from: image, quality: q)
        case .webp:
            // WebP encoding is not supported on iOS; rejected upfront by the Dart validator.
            return nil
        }
    }

    private static func heifData(from image: UIImage, quality: CGFloat) -> Data? {
        guard let cgImage = image.cgImage else { return nil }
        let ciImage = CIImage(cgImage: cgImage)
        let context = CIContext()
        let options: [CIImageRepresentationOption: Any] = [
            CIImageRepresentationOption(rawValue: kCGImageDestinationLossyCompressionQuality as String): quality
        ]
        return context.heifRepresentation(
            of: ciImage,
            format: .ARGB8,
            colorSpace: ciImage.colorSpace ?? CGColorSpaceCreateDeviceRGB(),
            options: options
        )
    }
}
