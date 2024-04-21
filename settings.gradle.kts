pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "Felis Repo"
            url = uri("https://repsy.io/mvn/0xjoemama/public/")
        }
    }
}

rootProject.name = "ModLoader"
includeBuild("loader_make")
include("loader")
include("api")
include("micromixin")
