// swift-tools-version: 6.2
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
                // Approachable concurrency: async funcs run on the caller's executor by default,
                // and `@concurrent` is the explicit opt-in to the global executor. Requires Swift 6.2.
                .enableUpcomingFeature("NonisolatedNonsendingByDefault")
            ]
        )
    ],
    swiftLanguageModes: [.v6]
)
