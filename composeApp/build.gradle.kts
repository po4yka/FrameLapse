import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            // Kotlin
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            // Dependency Injection
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Database
            implementation(libs.sqldelight.coroutines)

            // Lifecycle
            implementation(libs.lifecycle.runtime.compose)
        }

        androidMain.dependencies {
            // Compose
            implementation(compose.preview)

            // Kotlin
            implementation(libs.kotlinx.coroutines.android)

            // AndroidX
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)

            // Dependency Injection
            implementation(libs.koin.android)

            // Database
            implementation(libs.sqldelight.android.driver)

            // CameraX
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)

            // MediaPipe
            implementation(libs.mediapipe.tasks.vision)

            // Image Processing
            implementation(libs.androidx.exifinterface)
        }

        iosMain.dependencies {
            // Database
            implementation(libs.sqldelight.native.driver)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(libs.mockk)
            }
        }
    }
}

android {
    namespace = "com.po4yka.framelapse"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.po4yka.framelapse"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Allow unsigned release builds for CI
            signingConfig = null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

sqldelight {
    databases {
        create("FrameLapseDatabase") {
            packageName.set("com.po4yka.framelapse.data.local")
        }
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

kover {
    reports {
        total {
            filters {
                excludes {
                    classes(
                        "*_Factory",
                        "*BuildConfig",
                        "*ComposableSingletons*",
                        "*_Impl",
                    )
                    packages("*.generated.*")
                    annotatedBy("androidx.compose.ui.tooling.preview.Preview")
                }
            }
            xml {
                xmlFile = layout.buildDirectory.file("reports/kover/report.xml")
            }
            html {
                title = "FrameLapse Coverage Report"
                htmlDir = layout.buildDirectory.dir("reports/kover/html")
            }
        }
    }
}
