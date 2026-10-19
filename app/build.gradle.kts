plugins {
    id("com.android.application")
}

// Versionscode = Minuten seit 2025-01-01. Steigt dadurch lokal wie auf GitHub Actions immer,
// sodass jede neue APK ein Update der vorherigen ist, egal wo sie gebaut wurde.
val versionMinutes = (System.currentTimeMillis() / 60_000L - 28_928_160L).toInt()
val buildLabel = System.getenv("GITHUB_RUN_NUMBER") ?: "lokal"

android {
    namespace = "de.melvin.tvlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.melvin.tvlauncher"
        // Philips 55PUS8106 läuft mit Android 11 (API 30)
        minSdk = 30
        targetSdk = 34
        versionCode = versionMinutes
        versionName = "0.2.$buildLabel"
    }

    signingConfigs {
        // Fester Schlüssel im Repo, damit jede neue APK das Update der alten ist.
        // Reicht für eine private Sideload-App; für den Play Store bräuchte man einen geheimen Schlüssel.
        create("release") {
            storeFile = file("tvlauncher.jks")
            storePassword = "tvlauncher"
            keyAlias = "tvlauncher"
            keyPassword = "tvlauncher"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
}
