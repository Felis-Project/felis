package io.github.joemama.loader.make

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

object LoaderMakeConfigurations {
    // loading classpath dependencies
    lateinit var mcLibs: Configuration
    lateinit var modLoader: Configuration
    lateinit var loadingClasspath: Configuration

    lateinit var modImplementation: Configuration
    lateinit var modCompileOnly: Configuration
    lateinit var modRuntimeOnly: Configuration

    fun createConfigurations(project: Project) {
        this.modLoader = project.configurations.create("modLoader") { it.isCanBeResolved = true }
        this.mcLibs = project.configurations.create("minecraftLibrary") { it.isCanBeResolved = true }
        this.loadingClasspath = project.configurations.create("loadingClasspath") {
            it.extendsFrom(modLoader, mcLibs)
        }

        this.modImplementation = project.configurations.create("modImplementation")
        this.modCompileOnly = project.configurations.create("modCompileOnly") {
            it.extendsFrom(modImplementation)
            it.isCanBeResolved = true
        }
        this.modRuntimeOnly = project.configurations.create("modRuntimeOnly") {
            it.extendsFrom(modImplementation)
            it.isCanBeConsumed = true
        }

        project.configurations.apply {
            getByName("implementation").extendsFrom(
                this@LoaderMakeConfigurations.modImplementation,
                this@LoaderMakeConfigurations.loadingClasspath
            )
            getByName("compileOnly").extendsFrom(this@LoaderMakeConfigurations.modCompileOnly)
            getByName("api").extendsFrom(this@LoaderMakeConfigurations.modCompileOnly)
        }
    }
}