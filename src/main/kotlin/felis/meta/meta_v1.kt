@file:UseSerializers(PathSerializer::class, PersonMetadataImpl.Serializer::class)

package felis.meta

import felis.api.meta.*
import io.github.z4kn4fein.semver.Version
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import net.peanuuutz.tomlkt.TomlContentPolymorphicSerializer
import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.TomlTable
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.pathString

@Serializable
data class ModMetadataImpl(
    override val schema: Int,
    override val modid: String,
    override val name: String,
    override val version: Version,
    override val license: LicenseMetadataImpl? = null,
    override val description: DescriptionMetadataImpl? = null,
    @Contextual override val icon: Path? = null,
    @Contextual override val people: Map<String, List<PersonMetadata>> = emptyMap(),
    override val contact: ContactMetadataImpl? = null,
    override val entrypoints: List<EntrypointMetadataImpl> = emptyList(),
    override val transformations: List<TransformationMetadataImpl> = emptyList(),
    override val dependencies: List<DependencyMetadataImpl> = emptyList(),
) : ModMetadata

object PathSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("java.nio.Path", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Path = Paths.get(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: Path) = encoder.encodeString(value.pathString)
}

@Serializable
@JvmInline
value class LicenseMetadataImpl(override val license: String) : LicenseMetadata

@Serializable(with = DescriptionMetadataImpl.Serializer::class)
sealed interface DescriptionMetadataImpl : DescriptionMetadata {
    @Serializable
    @JvmInline
    value class Simple(override val description: String) : DescriptionMetadataImpl {
        override val slogan: String?
            get() = null
    }

    @Serializable
    data class Extended(override val description: String, override val slogan: String? = null) : DescriptionMetadataImpl

    object Serializer : TomlContentPolymorphicSerializer<DescriptionMetadataImpl>(DescriptionMetadataImpl::class) {
        override fun selectDeserializer(element: TomlElement): DeserializationStrategy<DescriptionMetadataImpl> =
            when (element) {
                is TomlTable -> Extended.serializer()
                else -> Simple.serializer()
            }
    }
}

@Serializable
data class ContactMetadataImpl(
    override val email: String? = null,
    override val issueTracker: String? = null,
    override val sources: String? = null,
    override val homepage: String? = null
) : ContactMetadata

data class PersonMetadataImpl(override val name: String, override val contact: ContactMetadata? = null) :
    PersonMetadata {
    @OptIn(ExperimentalSerializationApi::class)
    object Serializer : KSerializer<PersonMetadata> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("felis.meta.PersonMetadata") {
            element<String>("name")
            element<String>("contact", isOptional = true)
        }

        override fun deserialize(decoder: Decoder): PersonMetadata =
            decoder.decodeStructure(this.descriptor) {
                val name = decodeStringElement(descriptor, descriptor.getElementIndex("name"))
                val contact = decodeNullableSerializableElement(
                    descriptor,
                    descriptor.getElementIndex("contact"),
                    ContactMetadataImpl.serializer()
                )
                PersonMetadataImpl(name, contact)
            }

        override fun serialize(encoder: Encoder, value: PersonMetadata) {
            encoder.encodeStructure(this.descriptor) {
                encodeStringElement(descriptor, descriptor.getElementIndex("name"), value.name)
                encodeNullableSerializableElement(
                    descriptor,
                    descriptor.getElementIndex("contact"),
                    ContactMetadataImpl.serializer(),
                    value.contact as? ContactMetadataImpl
                )
            }
        }
    }
}

@Serializable
data class EntrypointMetadataImpl(override val id: String, override val specifier: String) : EntrypointMetadata

@Serializable
data class TransformationMetadataImpl(
    override val specifier: String,
    override val name: String,
    override val target: List<String>
) : TransformationMetadata

@Serializable
class DependencyMetadataImpl : DependencyMetadata
