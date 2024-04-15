package io.github.joemama.loader.make

import org.gradle.api.Project
import java.io.File
import javax.inject.Inject

abstract class LibraryFetcher {
    @get:Inject
    abstract val project: Project
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
    val libraries: Set<File> by lazy {
        val version = project.extensions.getByType(LoaderMakePlugin.Extension::class.java).version
        val root = this.project.objects.directoryProperty()
        root.set(this.librariesRoot)

        val versionMeta = LoaderMakePlugin.piston.getVersion(version)

        val libs = versionMeta.libraries
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
        this.project.dependencies.add("implementation", project.files(this@LibraryFetcher.libraries))
    }
}