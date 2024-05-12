package felis.launcher

import felis.side.Side
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object FelisLaunchEnvironment : OptionScope {
    val side: Side by option("felis.side") {
        enumValueOf(it)
    }
    val mods: List<Path> by option("felis.mods", default(emptyList())) {
        it.split(File.pathSeparator).filter(String::isNotEmpty).map(Paths::get)
    }
    val launcher: GameLauncher by option("felis.launcher", default(MinecraftLauncher())) {
        Class.forName(it).getDeclaredConstructor().newInstance() as GameLauncher
    }
    val printClassPath: Boolean by option("felis.print.cp", default(false)) {
        it.toBooleanStrict()
    }
    val audit: Path? by option("felis.audit", default(null)) {
        Paths.get(it)
    }
    val logger: Logger = LoggerFactory.getLogger(FelisLaunchEnvironment::class.java)
}