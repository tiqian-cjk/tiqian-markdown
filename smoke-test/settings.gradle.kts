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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        maven {
            name = "tiqianUnderTest"
            url = uri(tiqianRepository)
        }
        mavenCentral()
    }
}

rootProject.name = "markdown-compose-maven-smoke-test"
