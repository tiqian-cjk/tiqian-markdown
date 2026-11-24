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
    ?: error("Set -PtiqianRepository to the isolated Maven repository under test")
// Released org.tiqian modules, searched after the repository under test; see the
// root settings.gradle.kts.
val tiqianRegistryRepository = providers.gradleProperty("tiqianRegistryRepository")
    .orElse(providers.environmentVariable("TIQIAN_REGISTRY_REPOSITORY"))
    .orNull

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        maven {
            name = "tiqianUnderTest"
            url = uri(tiqianRepository)
        }
        if (tiqianRegistryRepository != null) {
            maven {
                name = "tiqianRegistry"
                url = uri(tiqianRegistryRepository)
                content {
                    includeGroup("org.tiqian")
                }
            }
        }
        mavenCentral()
    }
}

rootProject.name = "markdown-compose-maven-smoke-test"
