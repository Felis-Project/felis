import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.felis.dam)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.shadow)
    `maven-publish`
    application
    `java-library`
}

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
    shadowJarApi(libs.bundles.asm)
    shadowJarApi(libs.tomlkt)
    shadowJar(libs.clikt)
    shadowJarApi(libs.kotlin.coroutines)
    shadowJarApi(kotlin("reflect"))
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

application {
    mainClass = "io.github.joemama.loader.MainKt"
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            pom {
                artifactId = "felis"
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
