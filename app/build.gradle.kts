plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.kaviancopytrade.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.kaviancopytrade.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "BASE_URL", "\"https://kaviancopytrade.ir\"")
        buildConfigField("String", "ALLOWED_HOST", "\"kaviancopytrade.ir\"")
    }

    val ksPath = System.getenv("KEYSTORE_FILE")
    val ksPass = System.getenv("KEYSTORE_PASSWORD")
    val keyAliasEnv = System.getenv("KEY_ALIAS")
    val keyPassEnv = System.getenv("KEY_PASSWORD")

    signingConfigs {
        if (!ksPath.isNullOrBlank() && !ksPass.isNullOrBlank() && !keyAliasEnv.isNullOrBlank() && !keyPassEnv.isNullOrBlank()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = keyAliasEnv
                keyPassword = keyPassEnv
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
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
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.webkit:webkit:1.11.0")
}
