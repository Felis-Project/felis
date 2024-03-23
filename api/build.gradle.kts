plugins {
    id("io.github.joemama.loader.make")
    kotlin("jvm")
}

val asmVersion = "9.5"

dependencies {
    implementation(project(":loader"))
}