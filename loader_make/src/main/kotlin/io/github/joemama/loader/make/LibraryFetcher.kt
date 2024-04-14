package io.github.joemama.loader.make

import org.gradle.api.Project
import java.io.File

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
    val libraries by lazy {
        val root = this.project.objects.directoryProperty()
        root.set(this.librariesRoot)

        // TODO: Use an extension to configure this
        val version = LoaderMakePlugin.piston.getVersion(this.version)

        val libs = version.libraries
        val results = mutableSetOf<File>()
        for (chunk in libs.chunked(10)) {
            chunk.map { it.downloads.artifact }.map { artifact ->
                val file = root.file(artifact.path).get().asFile
                fetchFile(artifact.url, file)
            }.map { it.join() }.toCollection(results)
        }

        results
    }

    fun includeLibs() {
        this.project.dependencies.apply {
            add("implementation", project.files(this@LibraryFetcher.libraries))
        }
    }
}