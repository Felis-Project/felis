package io.github.joemama.loader.make

import kotlinx.serialization.json.Json
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import java.net.http.HttpClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LoaderMakePlugin : Plugin<Project> {
    companion object {
        private val taskExecutor: ExecutorService =
            Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors())
        val httpClient: HttpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .executor(this.taskExecutor)
            .build()
        val json: Json = Json {
            ignoreUnknownKeys = true
        }
        lateinit var piston: Piston
            private set
    }

    override fun apply(project: Project) {
        val implementation = project.configurations.maybeCreate("implementation")
        implementation.isCanBeResolved = true

        project.repositories.apply {
            mavenCentral()
            maven {
                it.url = project.uri("https://repo.spongepowered.org/repository/maven-public/")
                it.name = "Sponge"
            }
        }

        project.plugins.apply {
            apply("application")
            // TODO: apply kotlin plugin
        }

        piston = Piston(project)
        LibraryFetcher(project, "1.20.4").includeLibs()
        val gameJars = GameJars(project, "1.20.4").prepare()

        val javaExt = project.extensions.getByType(JavaPluginExtension::class.java)
        project.tasks.register("runGame", JavaExec::class.java) {
            it.group = "minecraft"
            it.dependsOn("build")
            it.classpath = javaExt.sourceSets.getByName("main").runtimeClasspath
            it.mainClass.set("io.github.joemama.loader.MainKt")

            it.args(
                "--mods", "run/mods",
                "--source", gameJars.client.path,
                "--accessToken", "0",
                "--version", "1.20.4-JoeLoader",
                "--gameDir", "run"
            )
        }
    }
}