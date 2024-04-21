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
        for (libId in libs.map(Library::name)) {
            this.project.dependencies.add("implementation", libId)
        }
        results
    }
}