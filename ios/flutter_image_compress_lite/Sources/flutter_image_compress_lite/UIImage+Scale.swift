import UIKit
import os

private let log = Logger(
    subsystem: "com.qeepcologne.flutter_image_compress_lite",
    category: "scale"
)

// UIGraphicsImageRendererFormat.default() picks up the main screen's traits, and two of them are
// wrong for an encoder:
//   - scale is the screen's (2× or 3× on real devices), so a renderer built from it produces a
//     `size × scale` PIXEL bitmap even though `draw(in:)` operates in points — encoding 4–9× more
//     pixels than the caller asked for, with the JPEG/PNG byte size blowing up correspondingly.
//   - preferredRange is .automatic, which renders in extended range on a wide-gamut device, so a
//     Display P3 source stays wide-gamut and a non-color-managed viewer shows it oversaturated.
// Pin both: scale = 1 makes pixels == points, .standard keeps the output sRGB.
private func pixelExactFormat() -> UIGraphicsImageRendererFormat {
    let f = UIGraphicsImageRendererFormat.default()
    f.scale = 1
    f.preferredRange = .standard
    return f
}

extension UIImage {
    /// Scales to `scaledSize` and, if `degrees` is non-zero, rotates in the same rasterization
    /// pass. Composing both transforms in one `UIGraphicsImageRenderer` avoids allocating an
    /// intermediate scaled bitmap and resamples the source pixels only once.
    func scaledAndRotated(to scaledSize: CGSize, degrees: CGFloat) -> UIImage {
        if ImageCompressPlugin.showLog {
            log.info("dst width = \(scaledSize.width)")
            log.info("dst height = \(scaledSize.height)")
            if degrees.truncatingRemainder(dividingBy: 360) != 0 {
                log.info("will rotate \(degrees)")
            }
        }

        // Scale-only fast path.
        if degrees.truncatingRemainder(dividingBy: 360) == 0 {
            let renderer = UIGraphicsImageRenderer(size: scaledSize, format: pixelExactFormat())
            return renderer.image { _ in
                draw(in: CGRect(origin: .zero, size: scaledSize))
            }
        }

        // Compose scale + rotate: renderer sized to the rotated bbox of the scaled image,
        // then translate to center, rotate, and draw into the scaled-size rect — resampled once.
        // Draw the UIImage, not its cgImage: the cgImage holds the raw, un-oriented pixels, while
        // scaledSize is in display orientation, so an EXIF-rotated photo would come out stretched.
        let radians = degrees * .pi / 180
        let finalSize = CGRect(origin: .zero, size: scaledSize)
            .applying(CGAffineTransform(rotationAngle: radians))
            .integral
            .size

        let renderer = UIGraphicsImageRenderer(size: finalSize, format: pixelExactFormat())
        return renderer.image { ctx in
            let cg = ctx.cgContext
            cg.translateBy(x: finalSize.width / 2, y: finalSize.height / 2)
            cg.rotate(by: radians)
            draw(in: CGRect(
                x: -scaledSize.width / 2,
                y: -scaledSize.height / 2,
                width:  scaledSize.width,
                height: scaledSize.height
            ))
        }
    }
}
