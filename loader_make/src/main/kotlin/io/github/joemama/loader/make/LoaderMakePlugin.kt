package io.github.joemama.loader.make

import kotlinx.serialization.json.Json
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaLibraryPlugin
import org.jetbrains.gradle.ext.IdeaExtPlugin
import org.jetbrains.kotlin.gradle.plugin.*
import java.net.http.HttpClient
import java.nio.file.Path
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

    abstract class Extension(@Inject private val project: Project, @Inject private val objects: ObjectFactory) {
        var version: String = "1.20.4"

        val gameJars: GameJars.JarResult by lazy { objects.newInstance(GameJars::class.java).prepare() }

        val libs: LibraryFetcher by lazy { objects.newInstance(LibraryFetcher::class.java) }

        val userCache: Path by lazy {
            project.gradle.gradleUserHomeDir
                .resolve("caches")
                .resolve("loader-make")
                .toPath()
        }

        val modRuntime: Configuration by lazy {
            project.configurations.create("modRuntime") {
                val runtimeOnly = project.configurations.getByName("runtimeClasspath")
                it.extendsFrom(runtimeOnly)
                it.isCanBeResolved = true
                it.isCanBeConsumed = false
                it.isVisible = false
            }
        }
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

        val downloadAssetsTask = project.tasks.register("downloadAssets", DownloadAssetsTask::class.java) {
            it.group = "minecraft"
            it.version.set(ext.version)
            it.assetDir.set(ext.userCache
                .resolve("assets")
                .let(Path::toFile)
                .apply { mkdirs() }
            )
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

        project.tasks.register("genSources", GenSourcesTask::class.java) {
            it.group = "minecraft"
            it.inputJar.set(ext.gameJars.merged)
            it.outputJar.set(ext.gameJars.merged.parentFile.resolve(ext.gameJars.merged.nameWithoutExtension + "-sources.jar"))
        }

        clientRun.gradleTask()
        serverRun.gradleTask()

        project.tasks.getByName("idea").doLast {
            clientRun.ideaRun()
            serverRun.ideaRun()
        }

        project.afterEvaluate {
            ext.libs.includeLibs()
            ext.gameJars
        }
    }
}