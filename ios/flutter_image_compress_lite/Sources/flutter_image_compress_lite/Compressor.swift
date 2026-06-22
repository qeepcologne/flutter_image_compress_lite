import UIKit
import CoreImage

enum Compressor {
    static func encode(image: UIImage, params: CompressParams) -> Data? {
        if ImageCompressPlugin.showLog {
            NSLog("width = %.0f", Double(image.size.width))
            NSLog("height = %.0f", Double(image.size.height))
            NSLog("minWidth = %d", params.minWidth)
            NSLog("minHeight = %d", params.minHeight)
            NSLog("format = %d", params.format.rawValue)
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
