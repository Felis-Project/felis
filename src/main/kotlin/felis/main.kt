package felis

import felis.launcher.GameLauncher
import felis.launcher.MinecraftLauncher
import felis.side.Side
import java.io.*
import java.nio.file.*
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Not meant to be used outside of this file.
 * Parses cli arguments and initializes the loader.
 */
object EnvironmentParser {
    sealed class DefaultValue<out T> {
        class Value<out T>(val t: T) : DefaultValue<T>()
        data object NoValue : DefaultValue<Nothing>()
    }

    class OptionKey<T>(
        private val name: String,
        private val default: DefaultValue<T> = DefaultValue.NoValue,
        private val creator: (String) -> T
    ) : ReadOnlyProperty<Any, T> {
        override fun getValue(thisRef: Any, property: KProperty<*>): T =
            System.getProperty(name)?.let(creator)
                ?: when (default) {
                    DefaultValue.NoValue -> throw IllegalStateException("Property $name must be specified or have a default value")
                    is DefaultValue.Value -> default.t
                }
    }

    val side: Side by OptionKey("felis.side") { enumValueOf(it) }
    val mods: List<Path> by OptionKey("felis.mods") { value ->
        value.split(File.pathSeparator).map { Paths.get(it) }
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

    override fun toString(): String {
        return "Felis Launch Environment: (side=$side, mods=$mods, launcher=$launcher, printClassPath=$printClassPath, audit=$audit)"
    }
}

fun main(args: Array<String>) {
    println("Felis running on ${System.getProperty("java.vendor")}: ${System.getProperty("java.version")} using arguments: ${args.contentToString()}")
    println(EnvironmentParser)

    // print the classpath
    if (EnvironmentParser.printClassPath) {
        val cp = System.getProperty("java.class.path").split(File.pathSeparator)
        for (s in cp) {
            println(s)
        }
    }

    // Initialize the ModLoader instance for this launch
    ModLoader.initLoader(
        side = EnvironmentParser.side,
        launcher = EnvironmentParser.launcher,
        mods = EnvironmentParser.mods,
        gameArgs = args,
        audit = EnvironmentParser.audit,
    )
}
