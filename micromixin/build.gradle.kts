plugins {
    kotlin("jvm")
    id("loader-make")
}

group = "io.github.joemama"
version = "1.0-ALPHA"

val mmVersion = "0.4.0-a20240227"

dependencies {
    modLoader(project(":loader"))
    modCompileOnly("org.stianloader:micromixin-annotations:$mmVersion")
    loadingClasspath("org.stianloader:micromixin-runtime:$mmVersion")
    loadingClasspath("org.stianloader:micromixin-transformer:$mmVersion")
}