allprojects {
    group = "io.github.joemama"
    version = "1.0-ALPHA"
}

plugins {
    kotlin("jvm") version "1.9.23" apply false
    `maven-publish`
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
