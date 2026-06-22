import Foundation
import ImageIO

enum ExifKeeper {
    static func applyExif(fromData sourceData: Data, to compressed: Data) -> Data {
        guard let source = CGImageSourceCreateWithData(sourceData as CFData, nil) else {
            return compressed
        }
        return apply(exif: exif(from: source), to: compressed)
    }

    static func applyExif(fromURL sourceURL: URL, to compressed: Data) -> Data {
        guard let source = CGImageSourceCreateWithURL(sourceURL as CFURL, nil) else {
            return compressed
        }
        return apply(exif: exif(from: source), to: compressed)
    }

    private static func exif(from source: CGImageSource) -> [CFString: Any]? {
        guard let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] else {
            return nil
        }
        var mutable = props
        // The image was already rotated during compression, so reset orientation to "up".
        mutable[kCGImagePropertyOrientation] = CGImagePropertyOrientation.up.rawValue
        return mutable
    }

    private static func apply(exif: [CFString: Any]?, to data: Data) -> Data {
        guard let exif = exif,
              let source = CGImageSourceCreateWithData(data as CFData, nil),
              let uti = CGImageSourceGetType(source) else {
            return data
        }
        let output = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(output, uti, 1, nil) else {
            return data
        }
        CGImageDestinationAddImageFromSource(dest, source, 0, exif as CFDictionary)
        return CGImageDestinationFinalize(dest) ? (output as Data) : data
    }
}
