package felis.meta

import felis.transformer.ContentCollection
import java.io.InputStream
import java.nio.file.Path

class ModSet(val mods: List<Mod>) : ContentCollection, Collection<Mod> by mods {
    override fun getContentPath(path: String): Path? = this.mods.firstNotNullOfOrNull { it.getContentPath(path) }
    override fun openStream(name: String): InputStream? = this.mods.firstNotNullOfOrNull { it.openStream(name) }
    override fun getContentPaths(path: String): List<Path> = this.mods.flatMap { it.getContentPaths(path) }
    operator fun get(modid: Modid): Mod? = this.mods.find { it.modid == modid }
}
