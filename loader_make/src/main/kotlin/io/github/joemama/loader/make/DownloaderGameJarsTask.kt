package io.github.joemama.loader.make

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class DownloaderGameJarsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val gameJarDir: DirectoryProperty

    @get:InputFile
    abstract val versionFile: RegularFileProperty

    @TaskAction
    fun downloadGameJars() {
        // TODO: Get jars
        TODO("Not implemented yet")
    }
}