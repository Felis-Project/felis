plugins {
    kotlin("jvm")
    id("loader-make")
    kotlin("plugin.serialization") version "1.9.23"
    `maven-publish`
    idea
}

val asmVersion = "9.5"
group = "io.github.joemama"
version = "1.0-ALPHA"

dependencies {
    // For transformations
    api("org.ow2.asm:asm-commons:$asmVersion")
    api("org.ow2.asm:asm-util:$asmVersion")
    // For metadata parsing
    implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
    // For command line argument parsing
    implementation("com.github.ajalt.clikt:clikt:4.3.0")
    // Mixin for MixinClassWriter for now + mixin functionality
    // TODO: Move this to a loader plugin
    api("org.spongepowered:mixin:0.8.5")
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

