	package io.github.joemama.loader.make
	
	import org.gradle.api.DefaultTask
	import org.gradle.api.file.DirectoryProperty
	import org.gradle.api.provider.ListProperty
	import org.gradle.api.provider.Property
	import org.gradle.api.tasks.*
	import java.io.File
	
	abstract class DownloadAssetsTask : DefaultTask() {
	    @get:PathSensitive(PathSensitivity.ABSOLUTE)
	    @get:InputDirectory
	    abstract val assetDir: DirectoryProperty
	
	    @get:Input
	    abstract val version: Property<String>
	
	    @get:OutputFiles
	    abstract val assets: ListProperty<File>
	
	    @TaskAction
	    fun downloadAssets() {
	        val assetDir = this.assetDir.get().asFile
	        assetDir.mkdirs()
	        val indices = assetDir.resolve("indexes")
	        indices.mkdirs()
	        val assetIndexMeta = LoaderMakePlugin.piston.getVersion(this.version.get()).assetIndex
	        val assetIndexFile =
	            fetchFile(assetIndexMeta.url, indices.resolve(assetIndexMeta.id + ".json")).join()
	        val assetIndex = LoaderMakePlugin.json.decodeFromString<AssetIndex>(assetIndexFile.readText())
	        val objects = assetDir.resolve("objects")
	        assetIndex.objects.values.sortedBy { it.size }.chunked(20).flatMap { it ->
	            it.map {
	                fetchFile(it.url, objects.resolve(it.path))
	            }.map { it.join() }
	        }
	    }
	}