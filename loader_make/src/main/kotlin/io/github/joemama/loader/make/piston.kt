package io.github.joemama.loader.make

import kotlinx.serialization.Serializable

class UnknownVersionException(version: String): IllegalArgumentException("Version $version could not be located in the version manifest")

@Serializable
data class VersionMeta(val libraries: List<Library>)

@Serializable
data class Library(val downloads: Downloads, val name: String)

@Serializable
data class Downloads(val artifact: Artifact)

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
data class Version(val id: String, val url: String, val sha1: String)