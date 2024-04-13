package io.github.joemama.loader.make

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

object LoaderMakeConfigurations {
    // loading classpath dependencies
    private lateinit var mcLibs: Configuration
    private lateinit var modLoader: Configuration
    lateinit var loadingClasspath: Configuration

    private lateinit var modImplementation: Configuration
    lateinit var modCompileOnly: Configuration
    lateinit var modRuntime: Configuration

    fun createConfigurations(project: Project) {
        this.modLoader = project.configurations.create("modLoader") { it.isCanBeResolved = false }
        this.mcLibs = project.configurations.create("minecraftLibrary") { it.isCanBeResolved = false }

        this.loadingClasspath = project.configurations.create("loadingClasspath") {
            it.extendsFrom(mcLibs, modLoader)
            it.isCanBeResolved = true
            it.isCanBeConsumed = false
        }

        this.modImplementation = project.configurations.create("modImplementation") { it.isCanBeResolved = false }
        this.modCompileOnly = project.configurations.create("modCompileOnly") {
            it.extendsFrom(modImplementation)
            it.isCanBeResolved = true
        }
        this.modRuntime = project.configurations.create("modRuntime") {
            it.extendsFrom(modImplementation)
            it.isCanBeResolved = true
        }

        project.configurations.apply {
            getByName("implementation").extendsFrom(loadingClasspath)
            getByName("compileOnly").extendsFrom(modCompileOnly)
            getByName("runtimeOnly").extendsFrom(modRuntime)
        }
    }
}