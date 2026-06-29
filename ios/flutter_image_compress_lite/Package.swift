// swift-tools-version: 6.3
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
            swiftSettings: [
                // Async funcs run on caller's executor unless marked @concurrent.
                .enableUpcomingFeature("NonisolatedNonsendingByDefault")
            ]
        )
    ],
    swiftLanguageModes: [.v6]
)
