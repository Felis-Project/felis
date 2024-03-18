pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    plugins {
    }
}

rootProject.name = "ModLoader"
includeBuild("loader_make")
include("loader")

