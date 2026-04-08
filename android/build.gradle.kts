plugins {
    id("com.android.library")
}

android {
    namespace = "com.fluttercandies.flutter_image_compress"
    compileSdk = 34

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.heifwriter:heifwriter:1.1.0")
}
