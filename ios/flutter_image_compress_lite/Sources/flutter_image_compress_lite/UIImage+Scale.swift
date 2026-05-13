import UIKit

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
            NSLog("scale = %.2f", Double(scaleRatio))
            NSLog("dst width = %.2f", Double(target.width))
            NSLog("dst height = %.2f", Double(target.height))
        }

        let renderer = UIGraphicsImageRenderer(size: target)
        return renderer.image { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
    }

    func rotated(byDegrees degrees: CGFloat) -> UIImage {
        if ImageCompressPlugin.showLog {
            NSLog("will rotate %.2f", Double(degrees))
        }
        let radians = degrees * .pi / 180
        let rotatedSize = CGRect(origin: .zero, size: size)
            .applying(CGAffineTransform(rotationAngle: radians))
            .integral
            .size

        let renderer = UIGraphicsImageRenderer(size: rotatedSize)
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
