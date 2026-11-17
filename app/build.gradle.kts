plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.orangesky.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.orangesky.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "1.6.4"
    }

    // Signing release: diisi lewat environment variable (dipakai GitHub Actions).
    // Kalau keystore tidak ada, release memakai debug key supaya tetap bisa diinstal untuk tes.
    val keystorePath = System.getenv("KEYSTORE_FILE")
    val hasKeystore = keystorePath != null && File(keystorePath).exists()

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 + buang resource tak terpakai: APK jauh lebih kecil.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName(if (hasKeystore) "release" else "debug")
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
    // Tanpa AppCompat & library splash: start lebih cepat, APK lebih kecil.
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    // Hanya untuk insets (edge-to-edge wajib di targetSdk 35+).
    implementation("androidx.core:core-ktx:1.15.0")
}
