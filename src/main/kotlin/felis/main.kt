package felis

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import felis.launcher.GameLauncher
import felis.launcher.MinecraftLauncher
import felis.side.Side
import java.io.*
import java.nio.file.Path

/**
 * Not meant to be used outside of this file.
 * Parses cli arguments and initializes the loader.
 */
internal class ModLoaderCommand : CliktCommand() {
    val mods: List<String> by option("--mods")
        .help("All directories and jar files to look for mods in separated by ':'")
        .split(File.pathSeparator)
        .default(emptyList())

    @Deprecated(message = "Use the GameLauncher system")
    val source: String? by option("--source")
    val gameLauncher: GameLauncher by option("--launcher")
        .help("The object or class instance to locate and run the game from")
        .convert { Class.forName(it).getDeclaredConstructor().newInstance() as GameLauncher }
        .defaultLazy { MinecraftLauncher() }
    val printClassPath: Boolean by option("--print-cp")
        .help("Print the jvm classpath")
        .boolean()
        .default(false)
    val side: Side by option("--side").enum<Side> { it.name }.required()
    val audit: Path? by option("--audit")
        .path(canBeDir = false)
        .help("Apply all transformations defined by mods to the source jar")

    // noop
    override fun run() = Unit
}

fun main(args: Array<String>) {
    println("Felis running using arguments: ${args.contentToString()}")
    /**
     *  delegate to [ModLoaderCommand]
     */
    val cmd = ModLoaderCommand()

    // Everything after -- is game arguments
    val ourArgs = args.takeWhile { it != "--" }
    // Using this since it clikt pollutes stacktraces big time
    cmd.main(ourArgs)

    // print the classpath
    if (cmd.printClassPath) {
        val cp = System.getProperty("java.class.path").split(File.pathSeparator)
        for (s in cp) {
            println(s)
        }
    }

    if (cmd.source != null)
        error("The --source options has been deprecated. Henceforth felis using the GameLauncher system to launch a game")

    // Initialize the ModLoader instance for this launch
    ModLoader.initLoader(
        mods = cmd.mods,
        side = cmd.side,
        launcher = cmd.gameLauncher,
        gameArgs = args.takeLastWhile { it != "--" }.toTypedArray(),
        audit = cmd.audit
    )
}
