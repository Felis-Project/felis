package io.github.joemama.loader.make

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class ApplyTransformationsTask : JavaExec() {
    private val cps = ModRun.createClasspaths(project)

    init {
        mainClass.set("io.github.joemama.loader.MainKt")
        classpath = project.objects.fileCollection().also { obs -> obs.from(cps.loading) }
    }

    @get:OutputFile
    abstract val auditJar: RegularFileProperty

    @TaskAction
    override fun exec() {
        val ext = project.extensions.getByType(LoaderMakePlugin.Extension::class.java)

        if (Os.isFamily(Os.FAMILY_MAC)) {
            jvmArgs("-XStartOnFirstThread")
        }

        args(
            "--mods", cps.gamePaths,
            "--source", ext.gameJars.merged.path,
            "--side", Side.CLIENT,
            "--audit", this.auditJar.get().asFile.path
        )
        super.exec()
    }
}