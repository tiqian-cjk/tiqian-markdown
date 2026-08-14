import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Sync
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

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

val tiqianDependencyVersion = providers.gradleProperty("tiqianDependencyVersion")
    .orElse(providers.environmentVariable("TIQIAN_DEPENDENCY_VERSION"))
    .orNull
    ?: rootProject.file(".tiqian-local.properties")
        .takeIf { it.isFile }
        ?.inputStream()
        ?.use { input ->
            Properties().apply { load(input) }.getProperty("version")?.trim()
        }
        ?.takeIf { it.isNotEmpty() }
    ?: providers.gradleProperty("tiqianVersion")
        .orElse(providers.environmentVariable("TIQIAN_VERSION"))
        .getOrElse("0.1.0-SNAPSHOT")

kotlin {
    jvmToolchain(25)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    android {
        namespace = "org.tiqian.markdown.compose"
        compileSdk = 37
        minSdk = 27
        androidResources.enable = true
        optimization {
            consumerKeepRules.file("consumer-rules.pro")
        }
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("org.tiqian:tiqian-compose:$tiqianDependencyVersion")
            api(compose.runtime)
            api(compose.ui)
            api(compose.components.resources)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.11.1")
            implementation("org.tiqian:math-compose:$tiqianDependencyVersion")
            implementation("org.tiqian:tiqian-font:$tiqianDependencyVersion")
            implementation("com.gallatinapps.syntaxmp:syntaxmp-tokenizer:0.3.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.tiqian:tiqian-shaping-skia:$tiqianDependencyVersion")
        }

        androidMain.dependencies {
            implementation("org.tiqian:tiqian-shaping-android-adapter:$tiqianDependencyVersion")
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

val prepareCommonComposeResources by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("src/commonMain/composeResources")) {
        // Lete is owned and loaded by math-compose. Do not publish the historical second copy.
        exclude("font/lete_sans_math_regular.otf")
        exclude("files/lete_sans_math/OFL.txt")
    }
    into(layout.buildDirectory.dir("generated/filteredComposeResources/commonMain"))
}

compose.resources {
    packageOfResClass = "org.tiqian.markdown.compose.generated.resources"
    customDirectory("commonMain", layout.dir(prepareCommonComposeResources.map { it.destinationDir }))
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
            val publicationName = name
            val targetSuffix = artifactId.removePrefix(project.name)
            artifactId = "markdown-compose$targetSuffix"
            artifact(
                tasks.register<Jar>("${publicationName}PublicationJavadocJar") {
                    archiveBaseName.set("${project.name}-$publicationName")
                    archiveClassifier.set("javadoc")
                    from(rootProject.file("LICENSE")) {
                        into("META-INF")
                    }
                },
            )
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
