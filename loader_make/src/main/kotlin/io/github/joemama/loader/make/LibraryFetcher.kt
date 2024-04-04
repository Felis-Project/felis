	package io.github.joemama.loader.make
	
	import org.gradle.api.Project
	
	class LibraryFetcher(private val project: Project, private val version: String) {
	    private val librariesRoot by lazy {
	        val libDir = project.gradle.gradleUserHomeDir
	            .resolve("caches")
	            .resolve("loader-make")
	            .resolve("libs")
	        libDir.mkdirs()
	        val res = this.project.objects.directoryProperty()
	        res.set(libDir)
	        res
	    }
	
	    // TODO: Filter libraries on OS
	    private fun downloadLibs() {
	        val root = this.project.objects.directoryProperty()
	        root.set(this.librariesRoot)
	
	        // TODO: Use an extension to configure this
	        val version = LoaderMakePlugin.piston.getVersion(this.version)
	
	        val libs = version.libraries
	        for (chunk in libs.chunked(10)) {
	            chunk.map { it.downloads.artifact }.map { artifact ->
	                val file = root.file(artifact.path).get().asFile
	                fetchFile(artifact.url, file)
	            }.forEach { it.join() }
	        }
	    }
	
	    fun includeLibs() {
	        this.downloadLibs()
	
	        this.project.dependencies.apply {
	            add("minecraftLibrary", project.files(librariesRoot.asFileTree.map { it.path }))
	        }
	    }
	}