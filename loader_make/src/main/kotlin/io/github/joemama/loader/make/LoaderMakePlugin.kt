package io.github.joemama.loader.make

import kotlinx.serialization.json.Json
import org.gradle.api.Plugin
import org.gradle.api.Project
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
        project.repositories.apply {
            mavenCentral()
            maven {
                it.url = project.uri("https://repo.spongepowered.org/repository/maven-public/")
                it.name = "Sponge"
            }
        }

        project.plugins.apply {
            apply("java")
        }

        val mcLibs = project.configurations.create("minecraftLibrary") {
            it.isCanBeResolved = true
            it.isTransitive = false
        }
        val modLoader = project.configurations.create("modLoader") {
            it.isCanBeResolved = true
        }
        project.configurations.getByName("implementation").extendsFrom(mcLibs, modLoader)

        piston = Piston(project)
        LibraryFetcher(project, "1.20.4").includeLibs()
        val gameJars = GameJars(project, "1.20.4").prepare()

        project.tasks.register("runGame", JavaExec::class.java) {
            it.dependsOn("build")
            it.group = "minecraft"
            it.mainClass.set("io.github.joemama.loader.MainKt")
            it.classpath = mcLibs + modLoader

            it.args(
                "--mods", project.layout.buildDirectory.files("libs").asPath,
                "--source", gameJars.client.path,
                "--accessToken", "0",
                "--version", "1.20.4-JoeLoader",
                "--gameDir", "run"
            )
        }
        project.tasks.register("genSources", GenSourcesTask::class.java) {
            it.group = "minecraft"
            it.inputJar.set(gameJars.client)
            it.outputJar.set(gameJars.client.parentFile.resolve(gameJars.client.nameWithoutExtension + "-sources"))
        }
    }
}