import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.3.20"
    id("com.android.kotlin.multiplatform.library") version "9.3.1"
    id("org.jetbrains.compose") version "1.11.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    `maven-publish`
    signing
}

group = "org.tiqian"
version = providers.gradleProperty("tiqianVersion")
    .orElse(providers.environmentVariable("TIQIAN_VERSION"))
    .getOrElse("0.1.0-SNAPSHOT")

val tiqianSuiteVersion = version.toString()

kotlin {
    jvmToolchain(25)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    android {
        namespace = "org.tiqian.markdown"
        compileSdk = 37
        minSdk = 27
        androidResources.enable = true
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("org.tiqian:tiqian-compose:$tiqianSuiteVersion")
            api(compose.runtime)
            api(compose.ui)
            api(compose.components.resources)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.11.1")
            implementation("org.tiqian.math:math-compose:$tiqianSuiteVersion")
            implementation("org.tiqian:tiqian-font:$tiqianSuiteVersion")
            implementation("com.gallatinapps.syntaxmp:syntaxmp-tokenizer:0.3.0")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.tiqian:tiqian-shaping-skia:$tiqianSuiteVersion")
        }

        androidMain.dependencies {
            implementation("org.tiqian:tiqian-shaping-native-font:$tiqianSuiteVersion")
            implementation("androidx.core:core-ktx:1.19.0")
        }

        getByName("androidDeviceTest").dependencies {
            implementation(kotlin("test"))
            implementation("androidx.test:runner:1.7.0")
            implementation("androidx.test:core:1.7.0")
            implementation("androidx.test.ext:junit:1.3.0")
        }
    }
}

compose.resources {
    packageOfResClass = "org.tiqian.markdown.generated.resources"
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}

publishing {
    repositories {
        maven {
            name = "central"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = providers.gradleProperty("mavenCentralUsername")
                    .orElse(providers.environmentVariable("MAVEN_CENTRAL_USERNAME"))
                    .orNull
                password = providers.gradleProperty("mavenCentralPassword")
                    .orElse(providers.environmentVariable("MAVEN_CENTRAL_PASSWORD"))
                    .orNull
            }
        }
    }
}

afterEvaluate {
    extensions.configure<PublishingExtension>("publishing") {
        publications.withType(MavenPublication::class.java).configureEach {
            artifact(javadocJar)
            pom {
                name.set("Tiqian Markdown Compose")
                description.set("A Compose Markdown renderer built on Tiqian's CJK paragraph and math layout engines.")
                url.set("https://github.com/tiqian-cjk/tiqian-markdown")
                licenses {
                    license {
                        name.set("Mozilla Public License 2.0")
                        url.set("https://www.mozilla.org/MPL/2.0/")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("123Duo3")
                        name.set("123Duo3")
                        email.set("123duo3@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/tiqian-cjk/tiqian-markdown.git")
                    developerConnection.set("scm:git:ssh://git@github.com/tiqian-cjk/tiqian-markdown.git")
                    url.set("https://github.com/tiqian-cjk/tiqian-markdown")
                }
            }
        }
    }

    val signingKey = providers.gradleProperty("signingKey")
        .orElse(providers.environmentVariable("SIGNING_KEY"))
        .orNull
    if (!signingKey.isNullOrBlank()) {
        extensions.configure<SigningExtension>("signing") {
            useInMemoryPgpKeys(
                providers.gradleProperty("signingKeyId")
                    .orElse(providers.environmentVariable("SIGNING_KEY_ID"))
                    .orNull,
                signingKey,
                providers.gradleProperty("signingPassword")
                    .orElse(providers.environmentVariable("SIGNING_PASSWORD"))
                    .orNull,
            )
            sign(extensions.getByType(PublishingExtension::class.java).publications)
        }
    }
}
