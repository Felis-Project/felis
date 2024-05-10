package felis.meta

import felis.transformer.ContentCollection
import felis.transformer.JarContentCollection
import felis.transformer.PathUnionContentCollection
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isDirectory

data object ClasspathScanner : Scanner {
    override fun offer(accept: (ContentCollection) -> Unit) {
        val directoryPaths = mutableListOf<Path>()
        for (entry in System.getProperty("java.class.path").split(File.pathSeparator)) {
            val root = Paths.get(entry)
            if (root.isDirectory()) {
                directoryPaths.add(root)
                continue
            }

            check(root.extension == "jar") { "Classpath entries can either be jars or directories. Problem at $entry" }
            accept(JarContentCollection(root))
        }

        accept(PathUnionContentCollection(directoryPaths))
    }
}