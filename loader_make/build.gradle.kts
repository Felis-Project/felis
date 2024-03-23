plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
}

val asmVersion = "9.5"

repositories {
    mavenCentral()
}

dependencies {
    // For transformations
    implementation("org.ow2.asm:asm-commons:$asmVersion")
    implementation("org.ow2.asm:asm-util:$asmVersion")
    implementation("org.ow2.asm:asm-tree:$asmVersion")

    // For manifest parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("org.benf:cfr:0.152")
}

gradlePlugin {
    plugins.create("loader_make") {
        id = "io.github.joemama.loader.make"
        implementationClass = "io.github.joemama.loader.make.LoaderMakePlugin"
    }
}