pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "EmoExample"

// Build against the local Emo library sources, mirroring how the iOS example
// references the package at ../.. — `implementation("ai.desertant:emo")` is
// substituted by this included build.
includeBuild("../..")

include(":app")
