package felis.meta

import io.github.z4kn4fein.semver.Version
import java.nio.file.Path

/**
 * Mod Metadata schema, version 1
 */
interface ModMetadataSchemaV1 {
    val schema: Int // The schema version for this felis.mod.toml file, any file that does not have it, it considered invalid
    val modid: String
    val name: String
    val version: Version
    val license: LicenseMetadata?
    val description: DescriptionMetadata?
    val icon: Path?
    val contact: ContactMetadata?
    val people: Map<String, List<PersonMetadata>>
    val entrypoints: List<EntrypointMetadata>
    val transformations: List<TransformationMetadata>
    val dependencies: DependencyMetadata?
}