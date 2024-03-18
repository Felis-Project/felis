package io.github.joemama.loader.make

import kotlinx.serialization.json.Json
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.net.http.HttpClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LoaderMakePlugin : Plugin<Project> {
    companion object {
        // TODO: Don't be thread greedy by default???
        private val taskExecutor: ExecutorService =
            Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors())
        val httpClient: HttpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .executor(this.taskExecutor)
            .build()
        val json: Json = Json {
            ignoreUnknownKeys = true
        }
    }

    override fun apply(project: Project) {
        val gradleCache = project.gradle.gradleUserHomeDir.resolve("caches").resolve("loader-make")

        project.tasks.apply {
            val downloadManifest = register("downloadManifest", DownloadManifestTask::class.java) {
                it.group = "minecraft"
                it.description = "Fetch the version manifest file from PistonMeta"
                it.manifest.set(project.layout.buildDirectory.file("version_manifest_v2.json"))
                // TODO: Unhardcode using extensions
                it.versionFile.set(project.layout.buildDirectory.file("1.20.4.json"))
                it.versionTarget.set("1.20.4")
            }

            register("downloadLibraries", DownloadLibrariesTask::class.java) { it ->
                it.group = "minecraft"
                it.description = "Download the libraries required by the game and add the to the classpath"

                val libsDir = gradleCache.resolve("libs")
                libsDir.mkdirs()
                it.librariesRoot.set(libsDir)
                it.versionFile.set(downloadManifest.flatMap { it.versionFile })
            }

            register("downloadGameJars", DownloaderGameJarsTask::class.java) { it ->
                it.group = "minecraft"
                it.description = "Download the game jars"

                gradleCache.mkdirs()
                it.gameJarDir.set(gradleCache)
                it.versionFile.set(downloadManifest.flatMap { it.versionFile })
            }

            register("deobfuscateGameJars") {
                it.group = "minecraft"
                it.description = "Deobfuscate the game jars"
                it.dependsOn("downloadGameJars")
            }
            register("mergeGameJars") {
                it.group = "minecraft"
                it.description = "Merge the game jars into a super jar"
                it.dependsOn("deobfuscateGameJars")
            }
            register("decompileGame") {
                it.group = "minecraft"
                it.description = "Decompile the game"
                it.dependsOn("mergeGameJars")
            }
            register("downloadAssets") {
                it.group = "minecraft"
                it.description = "Download the minecraft asset files and indices"
                it.dependsOn("downloadManifest")
            }
        }
    }
}