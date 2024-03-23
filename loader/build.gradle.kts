plugins {
    kotlin("jvm")
    id("io.github.joemama.loader.make")
    kotlin("plugin.serialization") version "1.9.23"
}

group = "io.github.joemama"
version = "1.0-ALPHA"
val asmVersion = "9.5"

dependencies {
    // For transformations
    api("org.ow2.asm:asm-commons:$asmVersion")
    api("org.ow2.asm:asm-util:$asmVersion")
    // For metadata parsing
    implementation("com.akuleshov7:ktoml-core:0.5.1")
    // Mixin TODO: Move this to a loader plugin
    api("org.spongepowered:mixin:0.8.5")
}