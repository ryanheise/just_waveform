// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "just_waveform",
    platforms: [
        .iOS("12.0"),
        .macOS("10.14")
    ],
    products: [
        .library(name: "just-waveform", targets: ["just_waveform"])
    ],
    dependencies: [],
    targets: [
        .target(
            name: "just_waveform",
            dependencies: [],
            cSettings: [
                .headerSearchPath("include/just_waveform")
            ]
        )
    ]
)
