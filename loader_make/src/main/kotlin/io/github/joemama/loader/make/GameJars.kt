package io.github.joemama.loader.make

import org.gradle.api.Project
import java.io.File

class GameJars(private val project: Project, private val versionId: String) {
    data class Jars(val client: File, val server: File)

    private val version = LoaderMakePlugin.piston.getVersion(this.versionId)

    private val versionDir by lazy {
        val dir = this.project.gradle.gradleUserHomeDir
            .resolve("caches")
            .resolve("loader-make")
            .resolve("jars")
            .resolve(this.versionId)
        dir.mkdirs()
        dir
    }

    private val mappingsDir by lazy {
        val dir = this.project.gradle.gradleUserHomeDir
            .resolve("caches")
            .resolve("loader-make")
            .resolve("mappings")
            .resolve(this.versionId)
        dir.mkdirs()
        dir
    }

    fun prepare() {
        val jars = fetchJars()
        val mapped = remapJars(jars)

        this.project.dependencies.add("compileOnly", this.project.files(mapped.client))
    }

    private fun fetchJars(): Jars {
        val client = fetchFile(version.downloads.client.url, this.versionDir.resolve("$versionId-client.jar")).join()
        val server = fetchFile(version.downloads.server.url, this.versionDir.resolve("$versionId-server.jar")).join()
        return Jars(client, server)
    }

    private fun remapJars(jars: Jars): Jars {
        val clientMaps = fetchFile(
            version.downloads.clientMappings.url,
            this.mappingsDir.resolve("$versionId-client.txt")
        ).join()
        fetchFile(version.downloads.serverMappings.url, this.mappingsDir.resolve("$versionId-server.txt")).join()
        val remappedClient = JarRemapper(jars.client).remap(clientMaps)
        // TODO: Deal with the bundler
        // JarRemapper(jars.server).remap(serverMaps)
        return Jars(remappedClient.toFile(), jars.server)
    }
}