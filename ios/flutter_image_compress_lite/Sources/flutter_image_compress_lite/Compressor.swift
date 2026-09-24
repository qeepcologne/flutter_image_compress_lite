import UIKit
import CoreImage
import ImageIO
import os

private let log = Logger(
    subsystem: "com.qeepcologne.flutter_image_compress_lite",
    category: "compress"
)

enum Compressor {
    /// Decodes at the largest power-of-two subsample factor that still leaves the image at least as large as the
    /// output size, so a 12 MP photo bound for 1280 px isn't first decoded to a ~48 MB bitmap — the counterpart of
    /// Android's `inSampleSize` decode. The output size is computed from the source's header properties, not the
    /// subsampled image, so it can't drift with the decoder's rounding. ImageIO honors the factor for JPEG, HEIF,
    /// PNG and TIFF and ignores it for other formats. Unreadable properties fall back to a full decode.
    static func decode(_ data: Data, minWidth: Int, minHeight: Int) -> (image: UIImage, size: CGSize)? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let w = props[kCGImagePropertyPixelWidth] as? Int,
              let h = props[kCGImagePropertyPixelHeight] as? Int,
              w > 0, h > 0
        else {
            guard let image = UIImage(data: data) else { return nil }
            return (image, targetSize(image.size, minWidth: minWidth, minHeight: minHeight))
        }
        let orientation = (props[kCGImagePropertyOrientation] as? UInt32)
            .flatMap { CGImagePropertyOrientation(rawValue: $0) } ?? .up
        // UIImage.size is in display orientation, so the output size is too; orientations 5–8 swap the axes.
        let swapsAxes: Bool = switch orientation {
            case .left, .leftMirrored, .right, .rightMirrored: true
            default: false
        }
        let displaySize = swapsAxes ? CGSize(width: h, height: w) : CGSize(width: w, height: h)
        let size = targetSize(displaySize, minWidth: minWidth, minHeight: minHeight)
        let factor = subsampleFactor(displaySize, target: size)
        let options = [kCGImageSourceSubsampleFactor: factor] as CFDictionary
        guard let cgImage = CGImageSourceCreateImageAtIndex(source, 0, options) else { return nil }
        return (UIImage(cgImage: cgImage, scale: 1, orientation: UIImage.Orientation(orientation)), size)
    }

    static func encode(image: UIImage, size: CGSize, params: CompressParams) -> Data? {
        if ImageCompressPlugin.showLog {
            log.info("decoded width = \(image.size.width)")
            log.info("decoded height = \(image.size.height)")
            log.info("minWidth = \(params.minWidth)")
            log.info("minHeight = \(params.minHeight)")
            log.info("format = \(params.format.rawValue)")
        }
        let img = image.scaledAndRotated(to: size, degrees: CGFloat(params.rotate))
        return data(from: img, quality: params.quality, format: params.format)
    }

    /// Scales down to the minWidth/minHeight envelope, never up. Same formula as Android's `targetSize`.
    private static func targetSize(_ size: CGSize, minWidth: Int, minHeight: Int) -> CGSize {
        let minW = CGFloat(minWidth)
        let minH = CGFloat(minHeight)
        let scale = min(1, size.width / size.height < minW / minH ? minW / size.width : minH / size.height)
        if ImageCompressPlugin.showLog {
            log.info("scale = \(scale)")
        }
        return CGSize(width: floor(scale * size.width), height: floor(scale * size.height))
    }

    /// ImageIO only honors 2, 4 and 8. A zero target (minWidth = minHeight = 0) would never end the loop, so it
    /// decodes at full size as before.
    private static func subsampleFactor(_ size: CGSize, target: CGSize) -> Int {
        guard target.width > 0, target.height > 0 else { return 1 }
        var n = 1
        while n < 8,
              floor(size.width / CGFloat(n * 2)) >= target.width,
              floor(size.height / CGFloat(n * 2)) >= target.height {
            n *= 2
        }
        return n
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

private extension UIImage.Orientation {
    init(_ orientation: CGImagePropertyOrientation) {
        switch orientation {
        case .up: self = .up
        case .upMirrored: self = .upMirrored
        case .down: self = .down
        case .downMirrored: self = .downMirrored
        case .left: self = .left
        case .leftMirrored: self = .leftMirrored
        case .right: self = .right
        case .rightMirrored: self = .rightMirrored
        }
    }
}
