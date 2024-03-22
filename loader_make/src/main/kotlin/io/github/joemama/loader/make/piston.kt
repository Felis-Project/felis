package io.github.joemama.loader.make

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.gradle.api.Project
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

const val VERSION_MANIFEST: String = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

class Piston(private val project: Project) {
    private val manifestFile = project.layout.buildDirectory.file("version_manifest_v2.json")
    private val versionManifest: VersionManifest by lazy {
        if (!this.manifestFile.get().asFile.exists()) {
            LoaderMakePlugin.httpClient.sendAsync(
                HttpRequest.newBuilder().GET().uri(URI.create(VERSION_MANIFEST)).build(),
                HttpResponse.BodyHandlers.ofString()
            ).thenApply(HttpResponse<String>::body).thenApply {
                this.manifestFile.get().asFile.writeText(it)
            }.join()
        }
        LoaderMakePlugin.json.decodeFromString(this.manifestFile.get().asFile.readText())
    }

    fun getVersion(version: String): VersionMeta {
        val versionFile = this.project.layout.buildDirectory.file("$version.json")
        if (!versionFile.get().asFile.exists()) {
            val versionUrl = this.versionManifest[version].url
            LoaderMakePlugin.httpClient.sendAsync(
                HttpRequest.newBuilder(URI(versionUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            ).thenApply(HttpResponse<String>::body).thenApply {
                versionFile.get().asFile.writeText(it)
            }.join()
        }

        return LoaderMakePlugin.json.decodeFromString<VersionMeta>(versionFile.get().asFile.readText())
    }
}

class UnknownVersionException(version: String) :
    IllegalArgumentException("Version $version could not be located in the version manifest")

@Serializable
data class VersionDownloads(
    val client: DownloadItem,
    @SerialName("client_mappings") val clientMappings: DownloadItem,
    val server: DownloadItem,
    @SerialName("server_mappings") val serverMappings: DownloadItem
)

@Serializable
data class DownloadItem(val url: String /* size, sha1 */)

@Serializable
data class VersionMeta(val libraries: List<Library>, val downloads: VersionDownloads)

@Serializable
data class Library(val downloads: LibraryDownloads, val name: String)

@Serializable
data class LibraryDownloads(val artifact: Artifact)

// TODO: SHA-1 validation
@Serializable
data class Artifact(val url: String, val path: String /* sha1, size */)

@Serializable
data class VersionManifest(val latest: LatestVersion, val versions: List<Version>) {
    operator fun get(version: String): Version {
        return this.versions.find { it.id == version } ?: throw UnknownVersionException(version)
    }
}

@Serializable
data class LatestVersion(val release: String, val snapshot: String)

// Metadata printout for reference
// {
//     "id": "24w11a",
//     "type": "snapshot",
//     "url": "https://piston-meta.mojang.com/v1/packages/9bc42da3c197d0532c34636d370451b3d09cbba1/24w11a.json",
//     "time": "2024-03-14T14:29:10+00:00",
//     "releaseTime": "2024-03-14T14:21:33+00:00",
//     "sha1": "9bc42da3c197d0532c34636d370451b3d09cbba1",
//     "complianceLevel": 1
// }
@Serializable
data class Version(val id: String, val url: String /*val sha1: String*/)