package felis.meta

import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import net.peanuuutz.tomlkt.*
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.pathString

object ModMetadataExtendedSerializer : KSerializer<ModMetadataExtended> {
    override val descriptor: SerialDescriptor = ModMetadata.serializer().descriptor
    override fun deserialize(decoder: Decoder): ModMetadataExtended {
        val metadata = decoder.decodeSerializableValue(ModMetadata.serializer())
        val tomlDecoder = decoder as TomlDecoder
        val extra = tomlDecoder.decodeTomlElement().asTomlTable()
        return ModMetadataExtended(metadata, extra)
    }

    override fun serialize(encoder: Encoder, value: ModMetadataExtended) {
        encoder.encodeSerializableValue(ModMetadata.serializer(), value.metadata)
    }
}

object PathSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("java.nio.Path", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Path = Paths.get(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: Path) = encoder.encodeString(value.pathString)
}

object DescriptionSerializer : TomlContentPolymorphicSerializer<DescriptionMetadata>(DescriptionMetadata::class) {
    object Simple : KSerializer<DescriptionMetadata> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("felis.meta.SimpleDescription", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): DescriptionMetadata =
            DescriptionMetadata(decoder.decodeString(), null)

        override fun serialize(encoder: Encoder, value: DescriptionMetadata) =
            encoder.encodeString(value.description)
    }

    @OptIn(ExperimentalSerializationApi::class)
    object Extended : KSerializer<DescriptionMetadata> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("felis.meta.ExtendedDescription") {
            element<String>("description")
            element<String>("slogan", isOptional = true)
        }

        override fun deserialize(decoder: Decoder): DescriptionMetadata = decoder.decodeStructure(this.descriptor) {
            lateinit var description: String
            var slogan: String? = null
            do {
                when (val idx = decodeElementIndex(descriptor)) {
                    descriptor.getElementIndex("description") -> description = decodeStringElement(descriptor, idx)
                    descriptor.getElementIndex("slogan") -> slogan = decodeNullableSerializableElement(
                        descriptor,
                        idx,
                        String.serializer()
                    )

                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Invalid element index $idx")
                }
            } while (true)
            DescriptionMetadata(description, slogan)
        }

        override fun serialize(encoder: Encoder, value: DescriptionMetadata) =
            encoder.encodeStructure(this.descriptor) {
                encodeStringElement(descriptor, descriptor.getElementIndex("description"), value.description)
                encodeNullableSerializableElement(
                    descriptor,
                    descriptor.getElementIndex("slogan"),
                    String.serializer(),
                    value.slogan
                )
            }
    }

    override fun selectDeserializer(element: TomlElement): DeserializationStrategy<DescriptionMetadata> =
        when (element) {
            is TomlTable -> Extended
            else -> Simple
        }
}