import java.nio.file.Files
import java.nio.file.StandardOpenOption

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    }
}

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    `maven-publish`
    application
    `java-library`
}

repositories {
    mavenCentral()
    maven {
        name = "Felis Repo"
        url = uri("https://repsy.io/mvn/0xjoemama/public/")
    }
}

group = "felis"
version = "1.4.0-alpha"

dependencies {
    api(libs.bundles.asm)
    api(libs.tomlkt)
    api(libs.kotlin.coroutines)
    api(kotlin("reflect"))
    implementation(libs.slf4j)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("io.github.joemama:actually-tiny-remapper:1.1.0-alpha")
}

tasks.processResources {
    expand("version" to version)
}

application {
    mainClass = "felis.MainKt"
}

java {
    withSourcesJar()
    withJavadocJar()
    targetCompatibility = JavaVersion.VERSION_21
    sourceCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    target {
        jvmToolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }
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

tasks.create("launcherJson") {
    doLast {
        val libs = file("libs.txt").toPath()
        val writer = Files.newBufferedWriter(
            libs,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.CREATE
        )
        configurations.runtimeClasspath.get().resolvedConfiguration.lenientConfiguration.allModuleDependencies.forEach {
            if (it.moduleArtifacts.isNotEmpty())
                writer.write("${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}\n")
        }
        writer.close()
    }
}