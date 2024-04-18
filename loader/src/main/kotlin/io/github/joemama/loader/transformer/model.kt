package io.github.joemama.loader.transformer

import java.io.File
import java.io.InputStream
import java.net.JarURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.util.jar.JarFile

/**
 * Basically the implementation of what I like to call a "Jar Tree".
 *
 * @author 0xJoeMama
 */
interface ContentCollection {
    fun getContentUrl(name: String): URL?
    fun <R> withStream(name: String, action: (InputStream) -> R): R? = this.openStream(name)?.use(action)
    fun openStream(name: String): InputStream?
    fun getContentUrls(name: String): Collection<URL>
}

class NestedContentCollection(private val children: Iterable<ContentCollection>) : ContentCollection {
    override fun openStream(name: String): InputStream? =
        this.children.firstNotNullOfOrNull { it.openStream(name) }

    override fun getContentUrl(name: String): URL? =
        this.children.firstNotNullOfOrNull { it.getContentUrl(name) }

    override fun getContentUrls(name: String): Collection<URL> =
        this.children.flatMap { it.getContentUrls(name) }
}

data class JarContentCollection(private val file: File) : ContentCollection {
    private val jarFile = JarFile(this.file)
    private val url by lazy { this.file.toURI() }
    val path: Path = this.file.toPath()
    private val urlStart by lazy { URI.create("jar:${this.url}!/").toString() }

    constructor(path: Path) : this(path.toFile())

    override fun getContentUrl(name: String): URL? {
        val url = URI.create(this.urlStart + name).toURL()
        return runCatching {
            url.openConnection() as JarURLConnection
            url
        }.getOrNull()
    }

    override fun openStream(name: String): InputStream? = this.jarFile.getJarEntry(name)?.let {
        this.jarFile.getInputStream(it)
    }

    /**
     * We assume that a jar cannot contain multiple entries so we can safely do this
     */
    override fun getContentUrls(name: String): Collection<URL> =
        this.getContentUrl(name)?.let { listOf(it) } ?: emptyList()
}

object EmptyContentCollection : ContentCollection {
    override fun getContentUrl(name: String): URL? = null
    override fun openStream(name: String): InputStream? = null
    override fun getContentUrls(name: String): Collection<URL> = emptyList()
}