// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "flutter_image_compress_lite",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(name: "flutter-image-compress-lite", targets: ["flutter_image_compress_lite"])
    ],
    dependencies: [],
    targets: [
        .target(
            name: "flutter_image_compress_lite",
            dependencies: [],
            publicHeadersPath: "."
        )
    ]
)
