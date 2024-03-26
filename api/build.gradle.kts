plugins {
    kotlin("jvm")
    id("loader-make")
    `maven-publish`
}

dependencies {
    modLoader(project(":loader"))
}

publishing {
    repositories {
        maven {
            name = "Github"
            url = uri("https://maven.pkg.github.com/0xJoeMama/ModLoader")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
