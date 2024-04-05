package io.github.joemama.loader.make

import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.GradleTask
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings
import java.io.File

enum class Side {
    CLIENT, SERVER
}

data class ModRun(
    val project: Project,
    val name: String,
    val sourceJar: File,
    val side: Side,
    val args: List<String> = emptyList(),
    val taskDependencies: List<String> = emptyList()
) {
    fun ideaRun() {
        project.extensions.getByType(IdeaModel::class.java).project?.settings?.runConfigurations?.apply {
            val loggerCfgFile = project.layout.buildDirectory.file("log4j2.xml")
            create("Minecraft ${this@ModRun.name}", Application::class.java).apply {
                beforeRun { beforeRun ->
                    // copy the logger config
                    loggerCfgFile.get().asFile.apply {
                        if (!exists()) {
                            parentFile.mkdirs()
                            ModRun::class.java.classLoader.getResourceAsStream("log4j2.xml")?.readAllBytes()
                                ?.let {
                                    writeBytes(it)
                                }
                        }
                    }
                    // run a build task
                    beforeRun.add(GradleTask("build"))
                    // run extra tasks
                    this@ModRun.taskDependencies.map { GradleTask(it) }.forEach(beforeRun::add)
                }

                mainClass = "io.github.joemama.loader.MainKt"
                includeProvidedDependencies = false
                jvmArgs += listOf(
                    "-Dlog4j.configurationFile=${loggerCfgFile.get().asFile.path}",
                    "-cp", LoaderMakeConfigurations.loadingClasspath.files.joinToString(":") { it.path }
                )
                programParameters = listOf(
                    "--mods", this@ModRun.getModRuntime(),
                    "--source", this@ModRun.sourceJar.path,
                    "--side", this@ModRun.side.name,
                    "--args", this@ModRun.args.joinToString(" "),
                ).joinToString(" ")
            }
        }
    }

    private fun getModRuntime(): String {
        val modRuntime = LoaderMakeConfigurations.modRuntimeOnly.files
        modRuntime.add(project.extensions.getByType(BasePluginExtension::class.java).libsDirectory.get().asFile)
        return modRuntime.joinToString(":") { it.path }
    }

    fun gradleTask() {
        project.tasks.register("run${this.name}", JavaExec::class.java) { it ->
            val loggerCfgFile = project.layout.buildDirectory.file("log4j2.xml")
            it.doFirst {
                loggerCfgFile.get().asFile.apply {
                    if (!exists()) {
                        parentFile.mkdirs()
                        LoaderMakePlugin::class.java.classLoader.getResourceAsStream("log4j2.xml")?.readAllBytes()
                            ?.let {
                                writeBytes(it)
                            }
                    }
                }
            }

            it.dependsOn("build")
            this.taskDependencies.forEach(it::dependsOn)

            it.group = "minecraft"
            it.mainClass.set("io.github.joemama.loader.MainKt")
            it.classpath = LoaderMakeConfigurations.loadingClasspath

            it.jvmArgs(
                "-Dlog4j.configurationFile=${loggerCfgFile.get().asFile.path}"
            )
            it.args(
                "--mods", this.getModRuntime(),
                "--source", this.sourceJar.path,
                "--side", this.side.name,
                "--args", this.args.joinToString(" "),
            )
        }
    }
}