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
            log.info("src width = \(image.size.width)")
            log.info("src height = \(image.size.height)")
            log.info("minWidth = \(params.minWidth)")
            log.info("minHeight = \(params.minHeight)")
            log.info("format = \(params.format.rawValue)")
        }
        let img = image.scaledAndRotated(
            toMinWidth: CGFloat(params.minWidth),
            minHeight: CGFloat(params.minHeight),
            degrees: CGFloat(params.rotate)
        )
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
        case .webp, .avif:
            // WebP and AVIF encoding are not supported on iOS; rejected upfront by the Dart validator.
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
        // Fall back to sRGB, not DeviceRGB: a device space carries no profile, so the HEIC would be written
        // with pixel values no viewer can interpret. Matches upstream #358 and the sRGB the renderer produces.
        let colorSpace = ciImage.colorSpace ?? CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
        let data = context.heifRepresentation(
            of: ciImage,
            format: .ARGB8,
            colorSpace: colorSpace,
            options: options
        )
        // The data-returning API carries no error out-param, and the reason is only available from the
        // file-based writeHEIFRepresentation(of:to:…) — which would mean writing and re-reading a tmp file
        // just for a debug log. So record *that* the encode failed, not why. The caller turns nil into a
        // COMPRESS_ERROR on the channel either way.
        if data == nil, ImageCompressPlugin.showLog {
            log.warning("heic encoding returned no data")
        }
        return data
    }
}
