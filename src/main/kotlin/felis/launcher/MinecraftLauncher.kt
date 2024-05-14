package felis.launcher

import felis.ModLoader
import felis.meta.ModMetadata
import felis.side.Side
import felis.transformer.JarContentCollection
import felis.util.PerfCounter
import io.github.joemama.atr.JarRemapper
import io.github.joemama.atr.ProguardMappings
import io.github.z4kn4fein.semver.Version
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*


class MinecraftLauncher : GameLauncher, OptionScope {
    private val logger = LoggerFactory.getLogger(MinecraftLauncher::class.java)
    private val remap: Boolean by option("felis.minecraft.remap", default(false), String::toBooleanStrict)
    private val cachePath: Path by option("felis.minecraft.cache", default(Paths.get(".felis")), Paths::get)

    override fun instantiate(args: Array<String>): GameInstance {
        val cp = System.getProperty("java.class.path").split(File.pathSeparator).map { Paths.get(it) }
        val mainClass = when (ModLoader.side) {
            Side.CLIENT -> "net.minecraft.client.main.Main"
            Side.SERVER -> "net.minecraft.server.Main"
        }
        val mainClassFile = mainClass.replace(".", "/") + ".class"
        for (cpEntry in cp) {
            if (!cpEntry.exists() || cpEntry.isDirectory()) continue
            FileSystems.newFileSystem(cpEntry).use { cpJar ->
                if (cpJar.getPath(mainClassFile).exists()) {
                    val version = Files.readString(cpJar.getPath("version.json")).let {
                        Json.decodeFromString<JsonObject>(it)
                    }
                    // TODO: Handle server bundler here as well
                    val versionId = version["id"]!!.jsonPrimitive.content
                    val minecraftJar = if (!remap) {
                        this.logger.debug("Not remapping. felis.remap was false or not specified")
                        cpEntry
                    } else deobfuscate(cpEntry, versionId)

                    return GameInstance(
                        JarContentCollection(minecraftJar),
                        ModMetadata(
                            schema = 1, name = "Minecraft", modid = "minecraft", version = Version.parse(versionId)
                        ).extended(),
                        mainClass,
                        args
                    )
                }
            }
        }
        throw GameNotFound("Minecraft")
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun deobfuscate(obfuscatedJar: Path, versionId: String): Path {
        val deobfuscated = this.cachePath.resolve("remapped").resolve("$versionId.jar")
        // if the deobfuscated file exists, we can return(we assume our remapping went good last time)
        if (deobfuscated.exists()) return deobfuscated

        this.logger.info("Deobfuscating version $versionId as a remapped jar was not found in Felis' cache")
        this.logger.info("This launch will take longer than usual")
        val perfCounter = PerfCounter()
        val deobfuscatedJar = perfCounter.timed {
            val client = HttpClient.newHttpClient()
            // find the version manifest
            // TODO: Use the launcher's manifest in the future
            val manifestPath = this.cachePath.resolve("version_manifest_v2.json")
            if (!manifestPath.exists()) {
                manifestPath.createParentDirectories()
                client.send(
                    HttpRequest.newBuilder(URI.create(VERSION_MANIFEST)).GET().build(),
                    BodyHandlers.ofFile(manifestPath)
                )
            }

            // now we are certain the version manifest exists
            // check if the mappings exist
            val mappingsPath = this.cachePath.resolve("mappings").resolve("$versionId.proguard")
            // if not download them
            if (!mappingsPath.exists()) {
                mappingsPath.createParentDirectories()
                val manifest = Json.decodeFromString<JsonObject>(manifestPath.readText())
                val versionUrl = manifest.getValue("versions").jsonArray.first {
                    it.jsonObject.getValue("id").jsonPrimitive.content == versionId
                }.jsonObject.getValue("url").jsonPrimitive.content
                val iS = client.send(
                    HttpRequest.newBuilder(URI.create(versionUrl)).GET().build(),
                    BodyHandlers.ofInputStream()
                ).body()
                val version = iS.use { Json.decodeFromStream<JsonObject>(it) }
                val mappingUrl = version.getValue("downloads").jsonObject
                    .getValue("client_mappings").jsonObject
                    .getValue("url").jsonPrimitive.content

                client.send(
                    HttpRequest.newBuilder(URI.create(mappingUrl)).GET().build(),
                    BodyHandlers.ofFile(mappingsPath)
                )
            }
            deobfuscated.createParentDirectories()
            this.logger.info("Summoning ActuallyTinyRemapper")
            JarRemapper(obfuscatedJar).remap(ProguardMappings(mappingsPath.readText()), deobfuscated)
        }
        perfCounter.printSummary { _, total, _ ->
            this.logger.info("Finished deobfuscating Minecraft in ${total}s")
        }
        return deobfuscatedJar
    }

    override fun toString(): String = MinecraftLauncher::class.java.name

    companion object {
        const val VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    }
}
