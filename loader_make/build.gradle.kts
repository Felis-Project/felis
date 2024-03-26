plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    `maven-publish`
}

val asmVersion = "9.5"
group = "io.github.joemama"
version = "1.0-ALPHA"

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
        id = "loader-make"
        implementationClass = "io.github.joemama.loader.make.LoaderMakePlugin"
    }
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
