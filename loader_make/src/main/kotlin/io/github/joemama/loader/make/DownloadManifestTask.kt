package io.github.joemama.loader.make

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers

abstract class DownloadManifestTask : DefaultTask() {
    companion object {
        const val VERSION_MANIFEST: String = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    }

    @get:Input
    abstract val versionTarget: Property<String>

    @get:OutputFile
    abstract val manifest: RegularFileProperty

    @get:OutputFile
    abstract val versionFile: RegularFileProperty

    @TaskAction
    fun downloadVersionManifest() {
        if (!this.manifest.get().asFile.exists()) {
            LoaderMakePlugin.httpClient.sendAsync(
                HttpRequest.newBuilder().GET().uri(URI.create(VERSION_MANIFEST)).build(),
                BodyHandlers.ofString()
            ).thenApply(HttpResponse<String>::body).thenApply {
                this.manifest.get().asFile.writeText(it)
            }.join()
        }

        if (!this.versionFile.get().asFile.exists()) {
            val manifest: VersionManifest =
                LoaderMakePlugin.json.decodeFromString(this.manifest.asFile.get().readText())
            val versionUrl = manifest[this.versionTarget.get()].url
            LoaderMakePlugin.httpClient.sendAsync(
                HttpRequest.newBuilder(URI(versionUrl)).GET().build(),
                BodyHandlers.ofString()
            ).thenApply(HttpResponse<String>::body).thenApply {
                this.versionFile.get().asFile.writeText(it)
            }.join()
        }
    }
}