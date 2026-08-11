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

rootProject.name = "tiqian-markdown"

include(":preview")

// Android Studio's model importer cannot currently map Android-only modules from
// the Tiqian composite builds (for example :shaping:native-font). During IDE
// sync, resolve the same lockstep artifacts from Maven Local instead; normal
// Gradle and CI builds keep source substitution enabled.
val isIdeaSync = providers.systemProperty("idea.sync.active")
    .map(String::toBoolean)
    .getOrElse(false)
val useLocalTiqianCheckouts = providers.gradleProperty("useLocalTiqianCheckouts")
    .orElse(providers.environmentVariable("USE_LOCAL_TIQIAN_CHECKOUTS"))
    .map(String::toBoolean)
    .getOrElse(!isIdeaSync)

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
            substitute(module("org.tiqian:tiqian-font")).using(project(":font"))
            substitute(module("org.tiqian:tiqian-shaping-skia")).using(project(":shaping:skia"))
            substitute(module("org.tiqian:tiqian-shaping-native-font")).using(project(":shaping:native-font"))
        }
    }
}

val mathCheckout = providers.gradleProperty("mathCheckout").orNull
    ?: System.getenv("TIQIAN_MATH_CHECKOUT")
    ?: "../tiqian-math"
val mathSettings = file(mathCheckout).resolve("settings.gradle.kts")
if (useLocalTiqianCheckouts && mathSettings.isFile) {
    includeBuild(mathCheckout) {
        dependencySubstitution {
            substitute(module("org.tiqian.math:math-compose")).using(project(":frontend:math-compose"))
        }
    }
}
