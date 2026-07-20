import UIKit
import os

private let log = Logger(
    subsystem: "com.qeepcologne.flutter_image_compress_lite",
    category: "scale"
)

// UIGraphicsImageRendererFormat.default() picks up the main screen's scale (2× or 3× on real
// devices), so a renderer built from it produces a `size × scale` PIXEL bitmap even though
// `draw(in:)` operates in points. For our use that means encoding 4–9× more pixels than the
// caller asked for, and the JPEG/PNG byte size blows up correspondingly. Force scale = 1 so
// pixels == points and the produced UIImage matches the requested dimensions exactly.
private func pixelExactFormat() -> UIGraphicsImageRendererFormat {
    let f = UIGraphicsImageRendererFormat.default()
    f.scale = 1
    return f
}

extension UIImage {
    /// Scales to fit the min-width/min-height envelope and, if `degrees` is non-zero, rotates
    /// in the same rasterization pass. Composing both transforms in one `UIGraphicsImageRenderer`
    /// avoids allocating an intermediate scaled bitmap and resamples the source pixels only once.
    func scaledAndRotated(toMinWidth minWidth: CGFloat,
                          minHeight: CGFloat,
                          degrees: CGFloat) -> UIImage {
        let imgRatio = size.width / size.height
        let maxRatio = minWidth / minHeight
        let scaleRatio = min(1, imgRatio < maxRatio ? minWidth / size.width : minHeight / size.height)
        let scaledSize = CGSize(
            width:  floor(scaleRatio * size.width),
            height: floor(scaleRatio * size.height)
        )

        if ImageCompressPlugin.showLog {
            log.info("scale = \(scaleRatio)")
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
        // then translate to center, rotate, flip Y (CG's origin is bottom-left), and draw
        // the source cgImage into the scaled-size rect — CG resamples once during draw.
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
            cg.scaleBy(x: 1, y: -1)
            if let cgImage = cgImage {
                cg.draw(cgImage, in: CGRect(
                    x: -scaledSize.width / 2,
                    y: -scaledSize.height / 2,
                    width:  scaledSize.width,
                    height: scaledSize.height
                ))
            }
        }
    }
}
