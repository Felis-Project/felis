package felis.launcher

import felis.side.Side
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object FelisLaunchEnvironment {
    val side: Side by OptionKey("felis.side") {
        enumValueOf(it)
    }
    val mods: List<Path> by OptionKey("felis.mods", DefaultValue.Value(emptyList())) {
        it.split(File.pathSeparator).filter(String::isNotEmpty).map(Paths::get)
    }
    val launcher: GameLauncher by OptionKey("felis.launcher", DefaultValue.Value(MinecraftLauncher())) {
        Class.forName(it).getDeclaredConstructor().newInstance() as GameLauncher
    }
    val printClassPath: Boolean by OptionKey("felis.print.cp", DefaultValue.Value(false)) {
        it.toBooleanStrict()
    }
    val audit: Path? by OptionKey("felis.audit", DefaultValue.Value(null)) {
        Paths.get(it)
    }
    val printResolutionStages by OptionKey("felis.print.res.stages", DefaultValue.Value(false)) {
        it.toBooleanStrict()
    }
    val logger: Logger = LoggerFactory.getLogger(FelisLaunchEnvironment::class.java)
}