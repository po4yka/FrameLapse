plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.kover) apply false
}

// Detekt configuration for all subprojects
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
        baseline = file("${rootProject.projectDir}/config/detekt/baseline.xml")
        parallel = true
    }

    dependencies {
        val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
        "detektPlugins"(libs.findLibrary("detekt-compose-rules").get())
        "detektPlugins"(libs.findLibrary("detekt-formatting").get())
    }
}

// Spotless configuration for all projects
allprojects {
    apply(plugin = "com.diffplug.spotless")

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/*.kt")
            targetExclude("**/build/**/*.kt")
            ktlint("1.5.0")
                .setEditorConfigPath("${rootProject.projectDir}/.editorconfig")
                .editorConfigOverride(
                    mapOf(
                        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                    ),
                )
            trimTrailingWhitespace()
            indentWithSpaces(4)
            endWithNewline()
        }
        kotlinGradle {
            target("**/*.gradle.kts")
            targetExclude("**/build/**/*.gradle.kts")
            ktlint("1.5.0")
        }
    }
}

// Aggregate tasks
tasks.register("staticAnalysis") {
    group = "verification"
    description = "Runs all static analysis tools"
    dependsOn("spotlessCheck", "detekt", ":composeApp:lintDebug")
}

tasks.register("format") {
    group = "formatting"
    description = "Formats all code with Spotless"
    dependsOn("spotlessApply")
}
