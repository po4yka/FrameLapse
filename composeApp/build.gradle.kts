import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

kotlin {
    // Android library configuration using new KMP plugin
    androidLibrary {
        namespace = "com.po4yka.framelapse"
        compileSdk = 36
        minSdk = 29

        // Enable unit tests
        withHostTestBuilder {
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
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
            implementation(libs.lifecycle.viewmodel.compose)

            // Navigation 3 (Multiplatform)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel.navigation3)
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

        // Unit tests for Android (host tests in new plugin terminology)
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test.junit)
            implementation(libs.mockk)
        }
    }
}

sqldelight {
    databases {
        create("FrameLapseDatabase") {
            packageName.set("com.po4yka.framelapse.data.local")
        }
    }
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
