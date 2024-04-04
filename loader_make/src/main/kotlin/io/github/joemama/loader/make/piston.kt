	package io.github.joemama.loader.make
	
	import kotlinx.serialization.SerialName
	import kotlinx.serialization.Serializable
	import org.gradle.api.Project
	
	const val VERSION_MANIFEST: String = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
	
	class Piston(private val project: Project) {
	    private val manifestFile = project.layout.buildDirectory.file("version_manifest_v2.json")
	    private val versionManifest: VersionManifest by lazy {
	        if (!this.manifestFile.get().asFile.exists()) {
	            fetchFile(VERSION_MANIFEST, this.manifestFile.get().asFile).join()
	        }
	        LoaderMakePlugin.json.decodeFromString(this.manifestFile.get().asFile.readText())
	    }
	
	    fun getVersion(version: String): VersionMeta {
	        val versionUrl = this.versionManifest[version].url
	        val versionFile = this.project.layout.buildDirectory.file("$version.json")
	        if (!versionFile.get().asFile.exists()) {
	            fetchFile(versionUrl, versionFile.get().asFile).join()
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
	data class VersionMeta(val libraries: List<Library>, val downloads: VersionDownloads, val assetIndex: AssetIndexMeta)
	
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
	
	@Serializable
	data class AssetIndexMeta(val id: String, val url: String /*sha1, size, totalSize*/)
	
	@Serializable
	data class AssetIndex(val objects: Map<String, AssetObject>)
	
	const val RESOURCES_URL = "https://resources.download.minecraft.net/"
	
	@Serializable
	data class AssetObject(val hash: String, val size: ULong) {
	    val url by lazy {
	        RESOURCES_URL + this.path
	    }
	    val path by lazy {
	        hash.substring(0..1) + "/" + hash
	    }
	}