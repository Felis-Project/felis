package felis.meta

import kotlinx.serialization.*
import net.peanuuutz.tomlkt.TomlTable

@Serializable
data class Entrypoint(
    val id: String,
    val path: String
)

@Serializable
data class Transform(
    val name: String,
    val target: String,
    val path: String
)

@Serializable
data class ModMeta(
    val name: String,
    val modid: String,
    val version: String,
    val description: String = "",
    val entrypoints: List<Entrypoint> = listOf(),
    val transforms: List<Transform> = listOf(),
) {
    @Transient
    var toml: TomlTable = TomlTable.Empty
        internal set
}