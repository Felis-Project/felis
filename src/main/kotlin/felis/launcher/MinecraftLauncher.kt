package felis.launcher

import felis.ModLoader
import felis.meta.ModMeta
import felis.side.Side
import felis.transformer.JarContentCollection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.exists

class MinecraftLauncher : GameLauncher {
    override fun instantiate(args: Array<String>): GameInstance {
        val cp = System.getProperty("java.class.path").split(File.pathSeparator).map { Paths.get(it) }
        val mainClass = when (ModLoader.side) {
            Side.CLIENT -> "net.minecraft.client.main.Main"
            Side.SERVER -> "net.minecraft.server.Main"
        }
        val mainClassFile = mainClass.replace(".", "/") + ".class"
        for (cpEntry in cp) {
            FileSystems.newFileSystem(cpEntry).use { cpJar ->
                if (cpJar.getPath(mainClassFile).exists()) {
                    val version = Files.readString(cpJar.getPath("version.json")).let {
                        Json.decodeFromString<JsonObject>(it)
                    }
                    val id = version["id"]!!.jsonPrimitive.content
                    // TODO: Bring in atr deobfuscation here
                    return GameInstance(
                        JarContentCollection(cpEntry),
                        ModMeta(name = "Minecraft", modid = "minecraft", version = id),
                        mainClass,
                        args
                    )
                }
            }
        }
        throw GameNotFound("Minecraft")
    }
}
