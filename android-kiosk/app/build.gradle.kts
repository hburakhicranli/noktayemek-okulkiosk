plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.bizim.kiosk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bizim.kiosk"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        // Local escape hatch: hold the right half of the screen for 5s on any tablet to get
        // a PIN prompt. Same PIN on every device for now -- change here and redeploy.
        buildConfigField("String", "EXIT_PIN", "\"2021\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Loads/caches the idle-screensaver images (plain URLs, not Firebase Storage).
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
