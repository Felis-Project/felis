plugins {
    id("loader-make")
    `maven-publish`
    id("org.jetbrains.dokka") version "1.9.20"
}

group = "io.github.joemama"
version = "1.1-ALPHA"

loaderMake {
    version = "1.20.4"
}

dependencies {
    implementation(project(":loader"))
    implementation(project(":micromixin"))
}

tasks.processResources {
    filesMatching("mods.toml") {
        expand("version" to version)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            pom {
                artifactId = "api"
            }
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "Repsy"
            url = uri("https://repo.repsy.io/mvn/0xjoemama/public")
            credentials {
                username = System.getenv("REPSY_USERNAME")
                password = System.getenv("REPSY_PASSWORD")
            }
        }
    }
}
