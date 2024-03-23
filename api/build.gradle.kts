plugins {
    kotlin("jvm")
    id("io.github.joemama.loader.make")
}

val asmVersion = "9.5"

dependencies {
    modLoader(project(":loader"))
}