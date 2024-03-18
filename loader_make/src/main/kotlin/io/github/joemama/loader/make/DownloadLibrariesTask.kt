package io.github.joemama.loader.make

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import java.util.concurrent.CompletableFuture

abstract class DownloadLibrariesTask : DefaultTask() {
    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFile
    abstract val versionFile: RegularFileProperty

    @get:OutputDirectory
    abstract val librariesRoot: DirectoryProperty

    // TODO: Filter libraries on OS
    @TaskAction
    fun downloadLibraries() {
        val root = librariesRoot.get()
        val version = LoaderMakePlugin.json.decodeFromString<VersionMeta>(this.versionFile.get().asFile.readText())

        val libs = version.libraries
        for (chunk in libs.chunked(10)) {
            chunk.map { it.downloads.artifact }.map { artifact ->
                val file = root.file(artifact.path).asFile
                if (!file.exists()) {
                    file.parentFile.mkdirs()
                    file.createNewFile()
                    println("Downloading file ${artifact.url}")
                    downloadJar(artifact.url, file)
                } else {
                    CompletableFuture.completedFuture(Unit)
                }
            }.forEach {
                it.join()
            }
        }
    }
}