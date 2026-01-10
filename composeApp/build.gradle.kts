import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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
            binaryOption("bundleId", "com.po4yka.framelapse.composeapp")
        }

        // Configure cinterop for OpenCV wrapper
        iosTarget.compilations.getByName("main") {
            cinterops {
                create("opencv") {
                    defFile("src/nativeInterop/cinterop/opencv.def")
                    // Include path for the header files
                    includeDirs("src/nativeInterop/cinterop")
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            implementation(projects.domain)
            implementation(projects.data)
            implementation(projects.platform)
            implementation(projects.presentation)
            implementation(projects.ui)

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
            implementation(libs.kotlinx.datetime)

            // Dependency Injection
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            api(libs.koin.annotations)

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

            // CameraX
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)

            // MediaPipe
            implementation(libs.mediapipe.tasks.vision)

            // OpenCV
            implementation(libs.opencv.android)

            // Image Processing
            implementation(libs.androidx.exifinterface)
        }

        iosMain.dependencies {
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(projects.testUtils)
        }

        // Unit tests for Android (host tests in new plugin terminology)
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test.junit)
            implementation(libs.mockk)
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

// KSP compiler dependencies for Koin Annotations
dependencies {
    add("kspCommonMainMetadata", libs.koin.ksp.compiler)
    add("kspAndroid", libs.koin.ksp.compiler)
    add("kspIosX64", libs.koin.ksp.compiler)
    add("kspIosArm64", libs.koin.ksp.compiler)
    add("kspIosSimulatorArm64", libs.koin.ksp.compiler)
}

// Add generated KSP sources to commonMain
kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}

// Ensure KSP runs before compilation and other KSP tasks depend on metadata
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

// Ensure platform-specific KSP tasks depend on commonMain KSP
tasks.matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }.configureEach {
    dependsOn("kspCommonMainKotlinMetadata")
}

// KSP configuration for Koin Annotations
// KOIN_CONFIG_CHECK enabled for compile-time dependency verification.
// All modules including PlatformModule use annotation-based configuration.
ksp {
    arg("KOIN_CONFIG_CHECK", "true")
}
