package io.github.joemama.loader.make

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.GradleTask
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings
import java.io.File
import java.util.jar.JarFile

enum class Side {
    CLIENT, SERVER
}

data class ModRun(
    val project: Project,
    val name: String,
    val side: Side,
    val args: List<String> = emptyList(),
    val taskDependencies: List<String> = emptyList()
) {
    private val sourceJar by lazy { project.extensions.getByType(LoaderMakePlugin.Extension::class.java).gameJars.merged }

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

                val cps = createClasspaths()
                mainClass = "io.github.joemama.loader.MainKt"
                includeProvidedDependencies = false
                jvmArgs += listOf(
                    "-Dlog4j.configurationFile=${loggerCfgFile.get().asFile.path}",
                    "-cp", cps.loadingPaths,
                )
                programParameters = listOf(
                    "--mods", cps.gamePaths,
                    "--source", this@ModRun.sourceJar.path,
                    "--side", this@ModRun.side.name,
                    "--args", this@ModRun.args.joinToString(" "),
                ).joinToString(" ")
            }
        }
    }

    data class Classpaths(val loading: List<File>, val game: List<File>) {
        val gamePaths = this.game.joinToString(File.pathSeparator) { it.path }
        val loadingPaths = this.loading.joinToString(File.pathSeparator) { it.path }
    }

    private fun createClasspaths(): Classpaths {
        val loading = mutableListOf<File>()
        val game = mutableListOf<File>()
        for (mod in project.extensions.getByType(LoaderMakePlugin.Extension::class.java).modRuntime) {
            JarFile(mod).getJarEntry("mods.toml")?.let {
                game.add(mod)
            } ?: loading.add(mod)
        }

        for (consideredMod in project.configurations.getByName("considerMod")) {
            game.add(consideredMod)
        }

        val jar = project.tasks.getByName("jar") as Jar
        game.add(jar.archiveFile.get().asFile)
        return Classpaths(loading, game)
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
            val cps = this.createClasspaths()
            it.mainClass.set("io.github.joemama.loader.MainKt")
            it.classpath = project.objects.fileCollection().also {
                it.from(cps.loading)
            }

            it.jvmArgs(
                "-Dlog4j.configurationFile=${loggerCfgFile.get().asFile.path}"
            )
            it.args(
                "--mods", cps.gamePaths,
                "--source", this.sourceJar.path,
                "--side", this.side.name,
                "--args", this.args.joinToString(" ")
            )
        }
    }
}