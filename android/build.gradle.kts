plugins {
    id("com.android.library")
}

android {
    namespace = "com.fluttercandies.flutter_image_compress"
    compileSdk = 37

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }

    defaultConfig {
        minSdk = 24
    }
}
