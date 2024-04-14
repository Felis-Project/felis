package io.github.joemama.loader.make

import kotlinx.serialization.json.Json
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.jvm.tasks.Jar
import org.jetbrains.gradle.ext.IdeaExtPlugin
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.utils.provider
import java.net.http.HttpClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

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

    abstract class Extension {
        @get:Inject
        abstract val project: Project

        var version: String = "1.20.4"

        val gameJars by provider { GameJars(this.project, this.version).prepare() }
        val libs by provider { LibraryFetcher(this.project, this.version) }
    }

    override fun apply(project: Project) {
        piston = project.objects.newInstance(Piston::class.java)
        val ext = project.extensions.create("loaderMake", Extension::class.java)
        project.repositories.apply {
            mavenCentral()
            maven {
                it.url = project.uri("https://repo.spongepowered.org/repository/maven-public/")
                it.name = "Sponge"
            }
            maven {
                it.url = project.uri("https://repo.repsy.io/mvn/0xjoemama/public")
                it.name = "Loader Repo"
            }
            maven {
                it.url = project.uri("https://stianloader.org/maven/")
                it.name = "Stianloader"
            }
        }

        project.buildscript.apply {
            repositories.apply {
                gradlePluginPortal()
                mavenCentral()
            }
        }

        project.plugins.apply {
            apply(JavaLibraryPlugin::class.java)
            apply(IdeaExtPlugin::class.java)
            apply(KotlinPluginWrapper::class.java)
        }

        ext.libs.includeLibs()
        val downloadAssetsTask = project.tasks.register("downloadAssets", DownloadAssetsTask::class.java) {
            it.group = "minecraft"
            it.version.set(ext.version)
            it.assetDir.set(
                project.gradle.gradleUserHomeDir
                    .resolve("caches")
                    .resolve("loader-make")
                    .resolve("assets")
                    .apply { mkdirs() }
            )
        }

        project.tasks.withType(Jar::class.java) { jar ->
            val refmap = project.layout.buildDirectory.file("mixin.refmap.json")
            jar.doFirst {
                refmap.map { it.asFile }.get().apply {
                    createNewFile()
                    writeText("{}\n")
                }
            }
            jar.from(refmap)
        }

        val clientRun = ModRun(
            name = "Client",
            project = project,
            side = Side.CLIENT,
            args = listOf(
                "--accessToken", "0",
                "--version", "${ext.version}-JoeLoader",
                "--gameDir", "run",
                "--assetsDir", downloadAssetsTask.get().assetDir.get().asFile.path,
                "--assetIndex", piston.getVersion(ext.version).assetIndex.id
            ),
            taskDependencies = listOf("downloadAssets")
        )

        val serverRun = ModRun(
            name = "Server",
            project = project,
            side = Side.SERVER,
            args = listOf("nogui")
        )

        clientRun.gradleTask()
        serverRun.gradleTask()

        project.tasks.register("genSources", GenSourcesTask::class.java) {
            it.group = "minecraft"
            it.inputJar.set(ext.gameJars.merged)
            it.outputJar.set(ext.gameJars.merged.parentFile.resolve(ext.gameJars.merged.nameWithoutExtension + "-sources.jar"))
        }.get()

        project.tasks.register("genIdeaRuns") {
            it.group = "minecraft"
            it.doLast {
                clientRun.ideaRun()
                serverRun.ideaRun()
            }
        }
    }
}