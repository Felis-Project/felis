package felis.meta

import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.constraints.Constraint
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlElement
import java.nio.file.Path

// Only compatible with TOML
@Serializable(with = ModMetadataExtendedSerializer::class)
class ModMetadataExtended(
    val metadata: ModMetadata,
    private val toml: Map<String, TomlElement> = emptyMap()
) : ModMetadataSchemaV1 by metadata, Map<String, TomlElement> by toml

@Serializable
open class ModMetadata(
    override val schema: Int,
    override val modid: String,
    override val name: String,
    override val version: Version,
    override val license: LicenseMetadata? = null,
    override val description: DescriptionMetadata? = null,
    @Contextual override val icon: Path? = null,
    override val contact: ContactMetadata? = null,
    override val people: Map<String, List<PersonMetadata>> = emptyMap(),
    override val entrypoints: List<EntrypointMetadata> = emptyList(),
    override val transformations: List<TransformationMetadata> = emptyList(),
    override val dependencies: DependencyMetadata? = null
) : ModMetadataSchemaV1 {
    fun extended(): ModMetadataExtended = ModMetadataExtended(this)
}

@Serializable
@JvmInline
value class LicenseMetadata(val license: String)

@Serializable(with = DescriptionSerializer::class)
open class DescriptionMetadata(
    val description: String,
    val slogan: String? = null
)

@Serializable
open class EntrypointMetadata(
    val id: String,
    val specifier: String
)

@Serializable
open class TransformationMetadata(
    val name: String,
    val targets: List<String>,
    val specifier: String
)

@Serializable
open class ContactMetadata(
    val email: String? = null,
    val issueTracker: String? = null,
    val sources: String? = null,
    val homepage: String? = null,
    val other: Map<String, String> = emptyMap()
)

@Serializable
open class PersonMetadata(
    val name: String,
    val contact: ContactMetadata? = null,
)

@Serializable
open class DependencyMetadata(
    val requires: List<SingleDependencyMetadata> = emptyList(),
    val recommends: List<SingleDependencyMetadata> = emptyList(),
    val breaks: List<SingleDependencyMetadata> = emptyList(),
)

@Serializable
open class SingleDependencyMetadata(val modid: String, val version: Constraint) {
    override fun toString(): String {
        return "SingleDependencyMetadata(modid='$modid', version=$version)"
    }
}
