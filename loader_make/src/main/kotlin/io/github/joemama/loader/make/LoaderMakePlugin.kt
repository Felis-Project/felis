package io.github.joemama.loader.make

import kotlinx.serialization.json.Json
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.ide.idea.IdeaPlugin
import java.io.File
import java.net.http.HttpClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LoaderMakePlugin : Plugin<Project> {
    companion object {
        private val taskExecutor: ExecutorService =
            Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors())
        val httpClient: HttpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .executor(this.taskExecutor)
            .build()
        val json: Json = Json {
            ignoreUnknownKeys = true
        }
        lateinit var piston: Piston
            private set
    }

    override fun apply(project: Project) {
        project.repositories.apply {
            mavenCentral()
            maven {
                it.url = project.uri("https://repo.spongepowered.org/repository/maven-public/")
                it.name = "Sponge"
            }
            maven {
                it.url = project.uri("https://repo.repsy.io/mvn/0xjoemama/public")
                it.name = "Loader Repo"
            }
        }

        project.buildscript.apply {
            repositories.apply {
                gradlePluginPortal()
                mavenCentral()
            }
        }

        project.plugins.apply {
            apply("java")
            apply(IdeaPlugin::class.java)
        }

        val mcLibs = project.configurations.create("minecraftLibrary") {
            it.isCanBeResolved = true
            it.isTransitive = false
        }
        val modLoader = project.configurations.create("modLoader") {
            it.isCanBeResolved = true
        }
        val modImplementation = project.configurations.create("modImplementation") {
            it.isCanBeResolved = true
            it.isTransitive = false
        }
        project.configurations.getByName("implementation").extendsFrom(mcLibs, modLoader, modImplementation)

        piston = Piston(project)
        LibraryFetcher(project, "1.20.4").includeLibs()
        val gameJars = GameJars(project, "1.20.4").prepare()

        val downloadAssetsTask = project.tasks.register("downloadAssets", DownloadAssetsTask::class.java) {
            it.group = "minecraft"
            it.version.set("1.20.4")
            it.assetDir.set(
                project.gradle.gradleUserHomeDir
                    .resolve("caches")
                    .resolve("loader-make")
                    .resolve("assets")
                    .apply { mkdirs() }
            )
        }

        project.tasks.register("runClient", JavaExec::class.java) { it ->
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
            it.dependsOn("build", "downloadAssets")
            it.group = "minecraft"
            it.mainClass.set("io.github.joemama.loader.MainKt")
            it.classpath = mcLibs + modLoader

            val modFiles = mutableSetOf<File>()
            modFiles.addAll(modImplementation.files)
            modFiles.add(project.layout.buildDirectory.files("libs").singleFile)
            val modPaths = modFiles.joinToString(separator = ":") { it.path }
            it.jvmArgs(
                "-Dlog4j.configurationFile=${loggerCfgFile.get().asFile.path}"
            )
            it.args(
                "--mods", modPaths,
                "--source", gameJars.merged.path,
                "--side", "CLIENT",
                "--args",
                """
                --accessToken 0 
                --version 1.20.4-JoeLoader 
                --gameDir run 
                --assetsDir ${downloadAssetsTask.get().assetDir.get().asFile.path} 
                --assetIndex ${piston.getVersion("1.20.4").assetIndex.id}
                """.trimIndent().filter { it != '\n' }
            )
        }

        project.tasks.register("runServer", JavaExec::class.java) { it ->
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
            it.group = "minecraft"
            it.mainClass.set("io.github.joemama.loader.MainKt")
            it.classpath = mcLibs + modLoader

            val modFiles = mutableSetOf<File>()
            modFiles.addAll(modImplementation.files)
            modFiles.add(project.layout.buildDirectory.files("libs").singleFile)
            val modPaths = modFiles.joinToString(separator = ":") { it.path }
            it.jvmArgs(
                "-Dlog4j.configurationFile=${loggerCfgFile.get().asFile.path}"
            )
            it.args(
                "--mods", modPaths,
                "--source", gameJars.merged.path,
                "--side", "SERVER",
                "--args", "nogui"
            )
        }

        project.tasks.register("genSources", GenSourcesTask::class.java) {
            it.group = "minecraft"
            it.inputJar.set(gameJars.merged)
            it.outputJar.set(gameJars.merged.parentFile.resolve(gameJars.merged.nameWithoutExtension + "-sources"))
        }
        project.tasks.withType(Jar::class.java) { jar ->
            val refmap = project.layout.buildDirectory.file("mixin.refmap.json")
            jar.doFirst {
                refmap.map { it.asFile }.get().apply {
                    createNewFile()
                    writeText("{}\n")
                }
            }
            jar.from(refmap)
        }
    }
}