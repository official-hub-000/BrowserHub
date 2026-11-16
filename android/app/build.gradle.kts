import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.browserhub.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.browserhub.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val ksPath = keystoreProps.getProperty("storeFile") ?: System.getenv("KEYSTORE_FILE")
            if (!ksPath.isNullOrBlank() && rootProject.file(ksPath).exists()) {
                storeFile = rootProject.file(ksPath)
                storePassword = keystoreProps.getProperty("storePassword") ?: System.getenv("KEYSTORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("keyAlias") ?: System.getenv("KEY_ALIAS")
                keyPassword = keystoreProps.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        // 1. Official Google Play Store App Bundle (.aab) Flavor — 100% Play Policy Compliant
        create("playstore") {
            dimension = "distribution"
            buildConfigField("boolean", "PLAY_STORE_COMPLIANT", "true")
        }

        // 2. Direct Website / GitHub Unrestricted APK Flavor (.apk)
        create("universal") {
            dimension = "distribution"
            versionNameSuffix = "-universal"
            buildConfigField("boolean", "PLAY_STORE_COMPLIANT", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseSign = signingConfigs.findByName("release")
            signingConfig = if (releaseSign?.storeFile != null) {
                releaseSign
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    bundle {
        language {
            enableSplit = false
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
