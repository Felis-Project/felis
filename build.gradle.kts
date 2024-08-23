val felisRepo = "https://repsy.io/mvn/0xjoemama/public/"

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
        url = uri(felisRepo)
    }
}

group = "felis"
version = "1.10.0-alpha"

dependencies {
    api(libs.bundles.asm)
    api(libs.tomlkt)
    api(libs.kotlin.coroutines)
    api(kotlin("reflect"))
    api(libs.semver)

    implementation(libs.slf4j)
    implementation(libs.kotlin.json)
    implementation(libs.atr)
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
            url = uri(felisRepo)
            credentials {
                username = System.getenv("REPSY_USERNAME")
                password = System.getenv("REPSY_PASSWORD")
            }
        }
    }
}

tasks.register("launcherJson", LaunchConfigTask::class.java) {
    gameVersion.set("1.21")
    repoMap.put("io.github.joemama:actually-tiny-remapper:1.1.0-alpha", felisRepo)
    ignore.add("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.0")
    ignore.add("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.0")
    ignore.add("org.jetbrains:annotations:23.0.0")
    ignore.add("org.jetbrains:annotations:13.0")
    additional.put("felis:felis:${version}", felisRepo)
}
