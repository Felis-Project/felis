package felis.transformer

import java.io.InputStream
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

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
    override fun getContentUrl(name: String): URL? = this.children.firstNotNullOfOrNull { it.getContentUrl(name) }
    override fun openStream(name: String): InputStream? = this.children.firstNotNullOfOrNull { it.openStream(name) }
    override fun getContentUrls(name: String): Collection<URL> = this.children.flatMap { it.getContentUrls(name) }
}

data class JarContentCollection(val path: Path) : ContentCollection {
    private val fs = FileSystems.newFileSystem(this.path)

    override fun getContentUrl(name: String): URL? =
        fs.getPath(name).let { if (Files.exists(it)) it.toUri().toURL() else null }

    override fun openStream(name: String): InputStream? =
        fs.getPath(name).let { if (Files.exists(it)) Files.newInputStream(it) else null }

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