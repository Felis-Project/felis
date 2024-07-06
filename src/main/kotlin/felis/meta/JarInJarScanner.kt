package felis.meta

import felis.transformer.ContentCollection
import felis.transformer.JarContentCollection

class JarInJarScanner(private val others: List<Scanner>) : Scanner {
    override fun offer(accept: (ContentCollection) -> Unit) {
        val collections = mutableListOf<ContentCollection>()
        this.others.forEach { it.offer(collections::add) }

        for (cc in collections) {
            val jars = cc.withStream("jars/jars.data") { String(it.readAllBytes()).lines() }?.filter { it.isNotBlank() } ?: continue
            for (jar in jars) cc.getContentPath("jars/$jar")?.let { JarContentCollection(it) }?.let(accept)
        }
    }

    override fun toString(): String = "JarInJarScanner($others)"
}