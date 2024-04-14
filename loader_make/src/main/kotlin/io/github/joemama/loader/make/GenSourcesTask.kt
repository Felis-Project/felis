package io.github.joemama.loader.make

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.jetbrains.java.decompiler.main.Fernflower
import org.jetbrains.java.decompiler.main.decompiler.SingleFileSaver
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger

@CacheableTask
abstract class GenSourcesTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun genSources() {
        val output = this.outputJar.get().asFile
        println("Decompiling sources to $output")
        val fl = Fernflower(SingleFileSaver(output), emptyMap(), IFernflowerLogger.NO_OP)

        fl.addSource(this.inputJar.get().asFile)

        this.project.extensions.getByType(LoaderMakePlugin.Extension::class.java).libs.libraries.forEach {
            fl.addLibrary(it)
        }

        fl.decompileContext()

        println("Finished decompiling sources")
    }
}