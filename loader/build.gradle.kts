plugins {
    id("loader-make")
    kotlin("plugin.serialization") version "1.9.23"
    `maven-publish`
    id("org.jetbrains.dokka") version "1.9.20"
}

val asmVersion = "9.5"
group = "io.github.joemama"
version = "1.1-ALPHA"

dependencies {
    // For transformations
    api("org.ow2.asm:asm-commons:$asmVersion")
    api("org.ow2.asm:asm-util:$asmVersion")

    // For metadata parsing
    api("net.peanuuutz.tomlkt:tomlkt:0.3.7")

    // For command line argument parsing
    implementation("com.github.ajalt.clikt:clikt:4.3.0")

    // For the kotlin language adapter
    implementation(kotlin("reflect"))
}

tasks.processResources {
    expand("version" to version)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            pom {
                artifactId = "loader"
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