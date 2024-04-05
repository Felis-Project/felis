plugins {
    id("loader-make")
    `maven-publish`
}

group = "io.github.joemama"
version = "1.0-ALPHA"

dependencies {
    modLoader(project(":loader"))
    modImplementation(project(":micromixin"))
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
