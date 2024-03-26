plugins {
    kotlin("jvm")
    id("io.github.joemama.loader.make")
    `maven-publish`
}

dependencies {
    modLoader(project(":loader"))
}