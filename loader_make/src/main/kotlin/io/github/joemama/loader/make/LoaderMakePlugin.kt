package io.github.joemama.loader.make

import kotlinx.serialization.json.Json
import org.gradle.api.Plugin
import org.gradle.api.Project
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
        }

        piston = Piston(project)
        LibraryFetcher(project, "1.20.4").includeLibs()
        GameJars(project, "1.20.4").prepare()
    }
}