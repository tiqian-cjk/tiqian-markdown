pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "markdown-compose"

val useLocalTiqianCheckouts = providers.gradleProperty("useLocalTiqianCheckouts")
    .orElse(providers.environmentVariable("USE_LOCAL_TIQIAN_CHECKOUTS"))
    .map(String::toBoolean)
    .getOrElse(true)

val tiqianCheckout = providers.gradleProperty("tiqianCheckout").orNull
    ?: System.getenv("TIQIAN_CHECKOUT")
    ?: "../Tiqian"
val tiqianSettings = file(tiqianCheckout).resolve("settings.gradle.kts")
if (useLocalTiqianCheckouts && tiqianSettings.isFile) {
    val composeProject = if (file(tiqianCheckout).resolve("frontend/compose").isDirectory) {
        ":frontend:compose"
    } else {
        ":tiqian-compose"
    }
    includeBuild(tiqianCheckout) {
        dependencySubstitution {
            substitute(module("org.tiqian:tiqian-compose")).using(project(composeProject))
        }
    }
}

val mathCheckout = providers.gradleProperty("mathCheckout").orNull
    ?: System.getenv("MATH_COMPOSE_CHECKOUT")
    ?: "../math-compose"
val mathSettings = file(mathCheckout).resolve("settings.gradle.kts")
if (useLocalTiqianCheckouts && mathSettings.isFile) {
    includeBuild(mathCheckout) {
        dependencySubstitution {
            substitute(module("org.tiqian.math:math-compose")).using(project(":frontend:math-compose"))
        }
    }
}
