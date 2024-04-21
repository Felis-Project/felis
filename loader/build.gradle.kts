import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("loader-make")
    kotlin("plugin.serialization") version "1.9.23"
    `maven-publish`
    id("org.jetbrains.dokka") version "1.9.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

val asmVersion = "9.5"
group = "io.github.joemama"
version = "1.1-ALPHA"

val shadowJarApi: Configuration by configurations.creating {
    configurations.getByName("api").extendsFrom(this)
}
val shadowJar: Configuration by configurations.creating {
    extendsFrom(shadowJarApi)
    configurations.getByName("implementation").extendsFrom(this)
}

dependencies {
    // For transformations
    shadowJarApi("org.ow2.asm:asm-commons:$asmVersion")
    shadowJarApi("org.ow2.asm:asm-util:$asmVersion")

    // For metadata parsing
    shadowJarApi("net.peanuuutz.tomlkt:tomlkt:0.3.7")

    // For command line argument parsing
    shadowJar("com.github.ajalt.clikt:clikt:4.3.0")

    // For the kotlin language adapter
    shadowJarApi(kotlin("reflect"))

    shadowJarApi("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
}

tasks.processResources {
    expand("version" to version)
}

tasks.withType(ShadowJar::class.java) {
    configurations = listOf(shadowJar)
    minimize()
    // TODO: Why does this not work?
    exclude("it.unimi.dsi:fastutil:.")
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