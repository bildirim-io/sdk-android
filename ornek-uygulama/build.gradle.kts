plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "io.bildirim.ornek"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.bildirim.ornek"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":bildirim"))
    // Müşteri uygulaması da tam böyle ekler: SDK firebase-messaging'i kendisi getirmez.
    implementation(libs.firebase.messaging)
}
