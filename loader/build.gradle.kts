plugins {
    kotlin("jvm")
    id("io.github.joemama.loader.make")
    kotlin("plugin.serialization") version "1.9.23"
}

val asmVersion = "9.5"

dependencies {
    // For transformations
    api("org.ow2.asm:asm-commons:$asmVersion")
    api("org.ow2.asm:asm-util:$asmVersion")
    // For metadata parsing
    implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
    // Mixin TODO: Move this to a loader plugin
    api("org.spongepowered:mixin:0.8.5")
}