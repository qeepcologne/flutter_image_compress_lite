plugins {
    id("com.android.library")
}

android {
    namespace = "com.fluttercandies.flutter_image_compress"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    implementation("androidx.heifwriter:heifwriter:1.1.0")
}
