plugins {
    kotlin("jvm")
    id("loader-make")
    kotlin("plugin.serialization") version "1.9.23"
    `maven-publish`
}

val asmVersion = "9.5"

dependencies {
    // For transformations
    api("org.ow2.asm:asm-commons:$asmVersion")
    api("org.ow2.asm:asm-util:$asmVersion")
    // For metadata parsing
    implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
    // Mixin for MixinClassWriter for now + mixin functionality
    // TODO: Move this to a loader plugin
    api("org.spongepowered:mixin:0.8.5")
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
