// Host-less JVM tests for the plugin's Android code: compiles ../android/src/main/kotlin as-is and
// runs it under Robolectric's native graphics mode, i.e. the real BitmapFactory/Skia codecs.
// Kept out of android/build.gradle.kts so the published plugin module stays minimal.
plugins {
    id("com.android.library") version "9.4.1"
}

val flutterJar = file(
    "${System.getenv("FLUTTER_ROOT") ?: "${System.getProperty("user.home")}/flutter"}/bin/cache/artifacts/engine/android-arm64/flutter.jar"
)

android {
    namespace = "com.fluttercandies.flutter_image_compress.test"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    sourceSets {
        getByName("main") { kotlin.directories.add("../android/src/main/kotlin") }
    }

    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation("androidx.heifwriter:heifwriter:1.1.0")
    compileOnly(files(flutterJar))
    testImplementation(files(flutterJar))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    // Robolectric reaches into JDK internals that newer JDKs no longer open by default.
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
    testLogging.showStandardStreams = true
}

// Robolectric's own ASM may predate the running JDK's class-file version.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.ow2.asm") useVersion("9.10.1")
    }
}
