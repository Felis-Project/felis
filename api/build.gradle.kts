plugins {
    kotlin("jvm")
    id("io.github.joemama.loader.make")
}

dependencies {
    modLoader(project(":loader"))
}