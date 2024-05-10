package felis

import felis.api.meta.ModMetadata
import felis.api.meta.PersonMetadata
import felis.launcher.GameLauncher
import felis.launcher.MinecraftLauncher
import felis.meta.DescriptionMetadataImpl
import felis.meta.ModMetadataImpl
import felis.meta.PathSerializer
import felis.meta.PersonMetadataImpl
import felis.side.Side
import io.github.z4kn4fein.semver.Version
import kotlinx.serialization.encodeToString
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import net.peanuuutz.tomlkt.Toml
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.*
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

object FelisLaunchEnvironment {
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
            System.getProperty(name)?.let(creator) ?: when (default) {
                DefaultValue.NoValue -> throw IllegalStateException("Property $name must be specified or have a default value")
                is DefaultValue.Value -> default.t
            }
    }

    val side: Side by OptionKey("felis.side") { enumValueOf(it) }
    val mods: List<Path> by OptionKey("felis.mods", DefaultValue.Value(emptyList())) { value ->
        value.split(File.pathSeparator).filter { it.isNotEmpty() }.map { Paths.get(it) }
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

    val logger: Logger = LoggerFactory.getLogger(FelisLaunchEnvironment::class.java)

    override fun toString(): String =
        "Felis launching with options: (side=$side, mods=$mods, launcher=$launcher, printClassPath=$printClassPath, audit=$audit)"
}

fun main(args: Array<String>) {
    FelisLaunchEnvironment.logger.info("Felis running on ${System.getProperty("java.vendor")}: ${System.getProperty("java.version")} using arguments: ${args.contentToString()}")
    FelisLaunchEnvironment.logger.info("$FelisLaunchEnvironment")

    // print the classpath
    if (FelisLaunchEnvironment.printClassPath) {
        val cp = System.getProperty("java.class.path").split(File.pathSeparator)
        FelisLaunchEnvironment.logger.info("Printing ${cp.size} classpath entries")
        for (s in cp) {
            FelisLaunchEnvironment.logger.info(s)
        }
    }

    val metaBuilder = ModMetadata {
        modid = "hello"
        name = "My mod"
        version = Version.parse("1.2.0-alpha+build.2")
        description {
            description = "Hello world!"
            slogan = "From joe himself"
        }
        license = "MIT"
    }
    val newMeta = ModMetadataImpl(
        schema = 1,
        modid = "hello",
        name = "My mod",
        version = Version.parse("1.2.0"),
        description = DescriptionMetadataImpl.Extended("Hello", slogan = "hello world"),
        icon = Paths.get("asd"),
        people = mapOf("authors" to listOf(PersonMetadataImpl(name = "Stuff")))
    )
    val toml = Toml {
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule = SerializersModule {
            contextual(Path::class, PathSerializer)
            contextual(PersonMetadata::class, PersonMetadataImpl.Serializer)
            polymorphic(ModMetadata::class) {
                subclass(ModMetadataImpl::class)
            }
        }
    }
    val s = toml.encodeToString(newMeta)
    val s2 = toml.encodeToString(metaBuilder)
    println(s)
    println(s2)

    // Initialize the ModLoader instance for this launch
    ModLoader.initLoader(
        side = FelisLaunchEnvironment.side,
        launcher = FelisLaunchEnvironment.launcher,
        mods = FelisLaunchEnvironment.mods,
        gameArgs = args,
        audit = FelisLaunchEnvironment.audit,
    )
}
