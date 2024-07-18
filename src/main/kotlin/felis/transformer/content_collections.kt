package felis.transformer

import felis.ModLoader
import java.io.InputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream

data class JarContentCollection(val path: Path) : ContentCollection {
    private val fs = FileSystems.newFileSystem(this.path)

    override fun getContentPath(path: String): Path? =
        fs.getPath(path).let { if (it.exists()) it else null }

    override fun openStream(name: String): InputStream? =
        fs.getPath(name).let { if (it.exists()) Files.newInputStream(it) else null }

    /**
     * We assume that a jar cannot contain multiple entries so we can safely do this
     */
    override fun getContentPaths(path: String): List<Path> =
        this.getContentPath(path)?.let { listOf(it) } ?: emptyList()
}

data class PathUnionContentCollection(val paths: List<Path>) : ContentCollection {
    override fun getContentPath(path: String): Path? = this.paths.firstNotNullOfOrNull {
        val out = it / path
        if (out.exists()) out else null
    }

    override fun openStream(name: String): InputStream? = this.getContentPath(name)?.inputStream()

    override fun getContentPaths(path: String): List<Path> = this.paths
        .asSequence()
        .map { it / path }
        .filter { it.exists() }
        .toList()
}

/**
 * Load content through a ContentCollection
 * Priority is: mods -> game -> libs
 */
// TODO: Make this a class given to the TransformingClassLoader
data object RootContentCollection : ContentCollection {
    override fun getContentPath(path: String): Path? = this.findPrioritized { it.getContentPath(path) }
    override fun openStream(name: String): InputStream? = this.findPrioritized { it.openStream(name) }
    override fun getContentPaths(path: String): List<Path> = buildList {
        addAll(ModLoader.discoverer.mods.flatMap { it.getContentPaths(path) })
        addAll(ModLoader.discoverer.libs.flatMap { it.getContentPaths(path) })
    }

    private inline fun <T> findPrioritized(getter: (ContentCollection) -> T?) =
        ModLoader.discoverer.mods.firstNotNullOfOrNull(getter)
            ?: ModLoader.discoverer.libs.firstNotNullOfOrNull { getter(it) }
}