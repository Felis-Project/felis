plugins {
    kotlin("jvm") version "1.9.23"
    id("io.github.joemama.loader.make")
}

val asmVersion = "9.5"

dependencies {
    // For transformations
    implementation("org.ow2.asm:asm-commons:$asmVersion")
    implementation("org.ow2.asm:asm-util:$asmVersion")
    implementation("org.ow2.asm:asm-tree:$asmVersion")
    // For metadata parsing
    implementation("com.akuleshov7:ktoml-core:0.5.1")
}