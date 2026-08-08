import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.compose") version "1.11.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
}

val tiqianSuiteVersion = providers.gradleProperty("tiqianVersion")
    .orElse(providers.environmentVariable("TIQIAN_VERSION"))
    .getOrElse("0.1.0-SNAPSHOT")

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    implementation("org.tiqian:markdown-compose:$tiqianSuiteVersion")
    implementation("org.tiqian:tiqian-compose:$tiqianSuiteVersion")
    implementation(compose.desktop.currentOs)
}
