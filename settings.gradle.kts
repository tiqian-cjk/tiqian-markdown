import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val tiqianRepository = providers.gradleProperty("tiqianRepository")
    .orElse(providers.environmentVariable("TIQIAN_REPOSITORY"))
    .orNull
val tiqianLocalConfig = rootDir.resolve(".tiqian-local.properties")
val tiqianLocalVersion = tiqianLocalConfig.takeIf { it.isFile }
    ?.inputStream()
    ?.use { input ->
        Properties().apply { load(input) }.getProperty("version")?.trim()
    }
    ?.takeIf { it.isNotEmpty() }

if (tiqianLocalConfig.isFile && tiqianLocalVersion == null) {
    error("${tiqianLocalConfig.name} must define a non-empty version")
}
if (tiqianLocalVersion != null && !tiqianLocalVersion.endsWith("-SNAPSHOT")) {
    error("${tiqianLocalConfig.name} may only select a -SNAPSHOT version")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        if (tiqianRepository != null) {
            maven {
                name = "tiqianIntegration"
                url = uri(tiqianRepository)
                content {
                    includeGroup("org.tiqian")
                }
            }
        }
        if (tiqianLocalVersion != null) {
            mavenLocal {
                name = "tiqianLocalSnapshots"
                content {
                    includeGroup("org.tiqian")
                }
                mavenContent {
                    snapshotsOnly()
                }
            }
        }
        mavenCentral()
    }
}

rootProject.name = "tiqian-markdown"

include(":preview")
