plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.portalopen.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.portalopen.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0-launcher"
    }
    kotlinOptions { jvmTarget = "17" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes { release { isMinifyEnabled = false } }
}

dependencies {
    implementation("androidx.core:core:1.16.0")
}
