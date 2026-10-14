plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.qrpicker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.qrpicker"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Ký bằng debug key để cài được ngay trên giả lập
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Thư viện giải mã QR (chỉ phần core, không cần camera)
    implementation("com.google.zxing:core:3.5.3")
}
