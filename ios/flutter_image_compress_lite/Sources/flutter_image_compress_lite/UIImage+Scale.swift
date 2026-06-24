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
    func scaled(toMinWidth minWidth: CGFloat, minHeight: CGFloat) -> UIImage {
        let actualWidth = size.width
        let actualHeight = size.height
        let imgRatio = actualWidth / actualHeight
        let maxRatio = minWidth / minHeight
        var scaleRatio: CGFloat = imgRatio < maxRatio
            ? minWidth / actualWidth
            : minHeight / actualHeight
        scaleRatio = min(1, scaleRatio)
        let target = CGSize(
            width: floor(scaleRatio * actualWidth),
            height: floor(scaleRatio * actualHeight)
        )

        if ImageCompressPlugin.showLog {
            log.info("scale = \(scaleRatio)")
            log.info("dst width = \(target.width)")
            log.info("dst height = \(target.height)")
        }

        let renderer = UIGraphicsImageRenderer(size: target, format: pixelExactFormat())
        return renderer.image { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
    }

    func rotated(byDegrees degrees: CGFloat) -> UIImage {
        if ImageCompressPlugin.showLog {
            log.info("will rotate \(degrees)")
        }
        let radians = degrees * .pi / 180
        let rotatedSize = CGRect(origin: .zero, size: size)
            .applying(CGAffineTransform(rotationAngle: radians))
            .integral
            .size

        let renderer = UIGraphicsImageRenderer(size: rotatedSize, format: pixelExactFormat())
        return renderer.image { ctx in
            let cg = ctx.cgContext
            cg.translateBy(x: rotatedSize.width / 2, y: rotatedSize.height / 2)
            cg.rotate(by: radians)
            cg.scaleBy(x: 1, y: -1)
            if let cgImage = cgImage {
                cg.draw(cgImage, in: CGRect(
                    x: -size.width / 2,
                    y: -size.height / 2,
                    width: size.width,
                    height: size.height
                ))
            }
        }
    }
}
