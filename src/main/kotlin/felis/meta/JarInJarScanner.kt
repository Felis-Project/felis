package felis.meta

import felis.transformer.ContentCollection
import felis.transformer.JarContentCollection
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml
import java.io.FileNotFoundException

class JarInJarScanner(private val others: List<Scanner>) : Scanner {
    companion object {
        val toml = Toml { ignoreUnknownKeys = false }
    }

    @Serializable
    data class JarData(val jars: List<String>)

    override fun offer(accept: (ContentCollection) -> Unit) = this.others.forEach {
        it.offer { cc -> this.walkAndOffer(cc, accept) }
    }

    private fun walkAndOffer(cc: ContentCollection, accept: (ContentCollection) -> Unit) {
        val (jars) = cc.withStream("jars/jars.data.toml") { String(it.readAllBytes()) }
            ?.let { toml.decodeFromString(JarData.serializer(), it) } ?: return

        for (jar in jars) {
            val jarCc = cc.getContentPath("jars/$jar")?.let { JarContentCollection(it) }
                ?: throw FileNotFoundException("Could not locate jar $jar specified by jars.data.toml metadata")
            accept(jarCc)
            this.walkAndOffer(jarCc, accept)
        }
    }

    override fun toString(): String = "JarInJarScanner($others)"
}