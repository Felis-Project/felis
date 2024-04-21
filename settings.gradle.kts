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

rootProject.name = "felis"
include("testmod")
