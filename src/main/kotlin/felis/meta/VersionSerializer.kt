package felis.meta

import io.github.z4kn4fein.semver.VersionFormatException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class VersionSerializer : KSerializer<Version> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("felis.meta.Version", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Version {
        val versionString = decoder.decodeString()
        return try {
            val version = io.github.z4kn4fein.semver.Version.parse(versionString, false)
            SemanticVersion(version)
        } catch (e: VersionFormatException) { /* ignored */
            LexVersion(versionString)
        }
    }

    override fun serialize(encoder: Encoder, value: Version) =
        encoder.encodeString(value.toString())
}