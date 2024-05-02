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
}

group = "felis"
version = "1.3.1-alpha"

dependencies {
    api(libs.bundles.asm)
    api(libs.tomlkt)
    implementation(libs.clikt)
    api(libs.kotlin.coroutines)
    api(kotlin("reflect"))
    implementation("org.slf4j:slf4j-api:2.0.13")
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
