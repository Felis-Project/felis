pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "ModLoader"
includeBuild("loader_make")
include("loader")
include("api")

