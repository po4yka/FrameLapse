plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.po4yka.framelapse"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.po4yka.framelapse"
        minSdk = 29
        targetSdk = 36
        versionCode = (properties["VERSION_CODE"] as? String)?.toIntOrNull() ?: 1
        versionName = properties["VERSION_NAME"] as? String ?: "1.0.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "framelapse"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkAllWarnings = true
        checkDependencies = true
        htmlReport = true
        xmlReport = true
        sarifReport = true
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(projects.composeApp)

    // AndroidX Activity for setContent
    implementation(libs.androidx.activity.compose)

    // Koin for Android Application
    implementation(libs.koin.android)
}
