import org.gradle.api.Action
import org.gradle.api.Task
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.io.Serializable

private class NormalizeDebAction(
    private val script: File,
    private val debDirectory: File,
    private val workDir: File,
) : Action<Task>,
    Serializable {
    override fun execute(task: Task) {
        val packages = debDirectory.listFiles { file -> file.isFile && file.extension == "deb" }?.toList().orEmpty()
        check(packages.size == 1) { "Expected exactly one generated Debian package in $debDirectory, found ${packages.size}." }
        val deb = packages.single()
        val exitCode =
            ProcessBuilder("bash", script.absolutePath, deb.absolutePath, workDir.absolutePath)
                .inheritIO()
                .start()
                .waitFor()
        check(exitCode == 0) { "Debian dependency normalization failed with exit code $exitCode." }
    }
}

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
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)

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

val generatedDebDirectory =
    layout.buildDirectory
        .dir("compose/binaries/main/deb")
        .get()
        .asFile
val normalizeDebWorkDir =
    layout.buildDirectory
        .dir("tmp/normalizeDebDependencies")
        .get()
        .asFile
val normalizeDebScript = layout.projectDirectory.file("scripts/normalize-deb-dependencies.sh").asFile
private val normalizeDebAction = NormalizeDebAction(normalizeDebScript, generatedDebDirectory, normalizeDebWorkDir)

val normalizeDebDependencies =
    tasks.register("normalizeDebDependencies") {
        group = "compose desktop"
        description = "Normalizes and validates Compose-generated Debian runtime dependencies."
        doLast(normalizeDebAction)
    }

tasks.configureEach {
    if (name == "packageDeb") {
        dependsOn("createRuntimeImage")
        inputs.file(normalizeDebScript)
        doLast(normalizeDebAction)
    }
}
