package felis.api.meta

import felis.meta.*
import io.github.z4kn4fein.semver.Version
import java.nio.file.Path

interface ModMetadata {
    val schema: Int
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
    val dependencies: List<DependencyMetadata>
}

fun ModMetadata(init: ModMetadataBuilder.() -> Unit): ModMetadata {
    val builder = ModMetadataBuilder()
    init.invoke(builder)
    return ModMetadataImpl(
        name = builder.name,
        modid = builder.modid,
        version = builder.version,
        license = builder.license?.let { LicenseMetadataImpl(it) },
        description = builder.description,
        schema = 1,
        icon = builder.icon,
        contact = builder.contact,
        people = builder.people,
        transformations = builder.transformations,
        entrypoints = builder.entrypoints
    )
}

class ModMetadataBuilder internal constructor() {
    lateinit var modid: String
    lateinit var name: String
    lateinit var version: Version
    var license: String? = null
    var icon: Path? = null
    var description: DescriptionMetadataImpl? = null
    var contact: ContactMetadataImpl? = null
    var people: MutableMap<String, MutableList<PersonMetadata>> = mutableMapOf()
    val entrypoints = mutableListOf<EntrypointMetadataImpl>()
    val transformations = mutableListOf<TransformationMetadataImpl>()

    inline fun description(init: DescriptionBuilder.() -> Unit) {
        val builder = DescriptionBuilder()
        init.invoke(builder)
        this.description = DescriptionMetadataImpl.Extended(builder.description, builder.slogan)
    }

    fun description(desc: String) {
        this.description = DescriptionMetadataImpl.Simple(desc)
    }

    inline fun contact(init: ContactBuilder.() -> Unit) {
        val builder = ContactBuilder()
        init.invoke(builder)

        this.contact = ContactMetadataImpl(builder.email, builder.issueTracker, builder.sources, builder.homepage)
    }

    inline fun people(init: PeopleBuilder.() -> Unit) {
        val builder = PeopleBuilder(this.people)
    }

    fun entrypoint(id: String, spec: String) {
        this.entrypoints.add(EntrypointMetadataImpl(id, spec))
    }

    fun transformation(name: String, targets: List<String>, spec: String) {
        this.transformations.add(TransformationMetadataImpl(name, spec, targets))
    }

    class PeopleBuilder(val map: MutableMap<String, MutableList<PersonMetadata>>) {
        fun person(role: String, init: PersonBuilder.() -> Unit) {
            val builder = PersonBuilder()
            init.invoke(builder)
            this.map.getOrPut(role, ::mutableListOf).add(PersonMetadataImpl(builder.name, builder.contact))
        }

        class PersonBuilder {
            lateinit var name: String
            var contact: ContactMetadata? = null

            inline fun contact(init: ContactBuilder.() -> Unit) {
                val builder = ContactBuilder()
                init.invoke(builder)

                this.contact = ContactMetadataImpl(
                    builder.email, builder.issueTracker, builder.sources, builder.homepage
                )
            }
        }
    }

    class ContactBuilder {
        var email: String? = null
        val issueTracker: String? = null
        val sources: String? = null
        val homepage: String? = null
    }

    class DescriptionBuilder {
        lateinit var description: String
        var slogan: String? = null
    }
}

interface LicenseMetadata {
    val license: String
}

interface DescriptionMetadata {
    val description: String
    val slogan: String?
}

interface EntrypointMetadata {
    val id: String
    val specifier: String
}

interface TransformationMetadata {
    val name: String
    val target: List<String>
    val specifier: String
}

// TODO: Add custom data
interface ContactMetadata {
    val email: String?
    val issueTracker: String?
    val sources: String?
    val homepage: String?
}

interface PersonMetadata {
    val name: String
    val contact: ContactMetadata?
}

interface DependencyMetadata
