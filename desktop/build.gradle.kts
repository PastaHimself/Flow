import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "io.github.aedev.flow.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "Flow"
            packageVersion = providers.gradleProperty("flow.desktopVersion").get()
            description = "Privacy-respecting YouTube client with local recommendations"
            vendor = "Flow"

            linux {
                iconFile.set(project.file("src/main/resources/flow.png"))
                packageName = "flow"
                debMaintainer = "flow.aedev@gmail.com"
                menuGroup = "AudioVideo"
                appCategory = "video"
            }
        }
    }
}

val desktopPackageVersion = providers.gradleProperty("flow.desktopVersion").get()
val generatedDeb =
    layout.buildDirectory
        .file("compose/binaries/main/deb/flow_$desktopPackageVersion-1_amd64.deb")
        .get()
        .asFile
val normalizeDebWorkDir =
    layout.buildDirectory
        .dir("tmp/normalizeDebDependencies")
        .get()
        .asFile
val normalizeDebScript = layout.projectDirectory.file("scripts/normalize-deb-dependencies.sh").asFile

val normalizeDebDependencies =
    tasks.register<Exec>("normalizeDebDependencies") {
        group = "compose desktop"
        description = "Normalizes and validates Compose-generated Debian runtime dependencies."
        commandLine(
            "bash",
            normalizeDebScript.absolutePath,
            generatedDeb.absolutePath,
            normalizeDebWorkDir.absolutePath,
        )
    }

tasks.configureEach {
    if (name == "packageDeb") {
        dependsOn("createRuntimeImage")
        finalizedBy(normalizeDebDependencies)
    }
}
