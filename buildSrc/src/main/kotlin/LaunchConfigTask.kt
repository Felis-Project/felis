import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardOpenOption

@DisableCachingByDefault(because = "we dont wanna cache")
abstract class LaunchConfigTask : DefaultTask() {
    @get:Input
    abstract val gameVersion: Property<String>

    @get:Input
    abstract val repoMap: MapProperty<String, String>

    @get:Input
    abstract val ignore: ListProperty<String>

    @get:Input
    abstract val additional: MapProperty<String, String>

    @Serializable
    data class FbhJson(val serverVersion: String, val additionalLibraries: List<Library>)

    @Serializable
    data class VersionJson(
        val id: String,
        val inheritsFrom: String,
        val type: String,
        val libraries: List<Library>,
        val mainClass: String,
        val arguments: Arguments
    )

    @Serializable
    data class Library(val name: String, val url: String)

    @Serializable
    data class Arguments(val jvm: List<String>, val game: List<String>)

    @OptIn(ExperimentalSerializationApi::class)
    @TaskAction
    fun createLauncherJson() {
        val libs =
            project.configurations.getByName("runtimeClasspath").resolvedConfiguration.lenientConfiguration.allModuleDependencies.mapNotNull {
                if (it.moduleArtifacts.isNotEmpty()) {
                    val lib = "${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}"
                    if (lib !in this.ignore.get()) {
                        Library(
                            lib,
                            this.repoMap.getting(lib).getOrElse("https://repo.maven.apache.org/maven2/")
                        )
                    } else null
                } else null
            }.sortedBy { it.name } + this.additional.get().map { (lib, repo) -> Library(lib, repo) }

        val version = VersionJson(
            id = this.gameVersion.get() + "-Felis",
            inheritsFrom = this.gameVersion.get(),
            type = "release",
            libraries = libs,
            mainClass = "felis.MainKt",
            arguments = Arguments(
                game = emptyList(),
                jvm = listOf(
                    "-Dfelis.side=CLIENT",
                    "-Dfelis.mods=mods",
                    "-Dfelis.minecraft.remap=true"
                )
            )
        )

        val fbh = FbhJson(
            serverVersion = this.gameVersion.get(),
            additionalLibraries = libs
        )

        val libsFile = project.file("${gameVersion.get()}-Felis.json").toPath()
        val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        Files.newBufferedWriter(
            libsFile,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.CREATE
        ).use { writer ->
            writer.write(json.encodeToString(version))
        }
        Files.newBufferedWriter(
            libsFile.resolveSibling("server.config.json"),
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.CREATE
        ).use { writer ->
            writer.write(json.encodeToString(fbh))
        }
    }
}