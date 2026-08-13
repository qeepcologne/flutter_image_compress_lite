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

    /// Keys that describe the *source* pixel buffer rather than the metadata worth keeping. The pipeline scales and
    /// re-encodes, so carrying them over writes claims that contradict the output: a Display-P3 `ProfileName` on an
    /// sRGB JPEG makes color-managed viewers apply the wrong transform, `HasAlpha` from a transparent PNG contradicts
    /// a JPEG that has no alpha channel, and the pixel dimensions are simply stale.
    private static var sourceOnlyKeys: [CFString] {[
        kCGImagePropertyPixelWidth,
        kCGImagePropertyPixelHeight,
        kCGImagePropertyFileSize,
        kCGImagePropertyProfileName,
        kCGImagePropertyColorModel,
        kCGImagePropertyDepth,
        kCGImagePropertyHasAlpha,
        kCGImagePropertyIsFloat,
        kCGImagePropertyIsIndexed,
    ]}

    private static func exif(from source: CGImageSource) -> [CFString: Any]? {
        guard let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] else {
            return nil
        }
        var mutable = props
        for key in sourceOnlyKeys {
            mutable.removeValue(forKey: key)
        }
        // The image was already rotated during compression, so reset orientation to "up" — in the TIFF dictionary
        // too, since a viewer that reads that tag instead of the top-level one would otherwise rotate a second time.
        mutable[kCGImagePropertyOrientation] = CGImagePropertyOrientation.up.rawValue
        if var tiff = mutable[kCGImagePropertyTIFFDictionary] as? [CFString: Any] {
            tiff[kCGImagePropertyTIFFOrientation] = CGImagePropertyOrientation.up.rawValue
            mutable[kCGImagePropertyTIFFDictionary] = tiff
        }
        // Same staleness as PixelWidth/Height, in the EXIF dictionary's own spelling.
        if var exif = mutable[kCGImagePropertyExifDictionary] as? [CFString: Any] {
            exif.removeValue(forKey: kCGImagePropertyExifPixelXDimension)
            exif.removeValue(forKey: kCGImagePropertyExifPixelYDimension)
            mutable[kCGImagePropertyExifDictionary] = exif
        }
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
