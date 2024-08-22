plugins {
    alias(libs.plugins.felis.dam)
}

group = "io.github.joemama"
version = "1.0.0-alpha"

loaderMake {
    version = "1.21.1-rc1"
}

dependencies {
    implementation(project(":"))
}