package felis.meta

import java.nio.file.Path

/**
 * Mod Metadata schema, version 1
 * @author 0xJoeMama
 * @since May 2024
 */
interface ModMetadataSchemaV1 {
    /**
     * The schema version for this felis.mod.toml file, any file that does not have it, it considered invalid
     */
    val schema: Int

    /**
     * The modid of the current mod
     */
    val modid: String

    /**
     * The display name of the current mod
     */
    val name: String

    /**
     * The version of the mod, (currently only) compatible the semver spec
     * @sample 1.2.0, 1.6.0-alpha, 0.0.1-alpha+buildv3.20240402
     */
    val version: Version

    /**
     * The license of the mod. This is not checked right now and can therefore take any values.
     * It is recommened however to use one of the licenses listed [here](https://choosealicense.com/licenses/)
     */
    val license: LicenseMetadata?

    /**
     * The [description][DescriptionMetadata] of the mod.
     */
    val description: DescriptionMetadata?

    /**
     * A path to the icon of the mod. This path is relative to the [felis.transformer.RootContentCollection], so it must be unique
     * @sample 'modid.png' and not 'icon.png'
     */
    val icon: Path?

    /**
     * Information to contact the maintainers or authors of the mod.
     * @see ContactMetadata
     */
    val contact: ContactMetadata?

    /**
     * Information about people who contributed to the mod
     */
    val people: Map<String, List<PersonMetadata>>

    /**
     * Entrypoints this mod provides.
     * @see EntrypointMetadata
     */
    val entrypoints: List<EntrypointMetadata>

    /**
     * Transformations this mod provides.
     * @see TransformationMetadata
     */
    val transformations: List<TransformationMetadata>

    /**
     * Dependency specification for this mod
     * @see DependencyMetadata
     */
    val dependencies: DependencyMetadata?
}