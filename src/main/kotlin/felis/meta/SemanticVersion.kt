package felis.meta

data class SemanticVersion(private val delegate: io.github.z4kn4fein.semver.Version) : Version {
    constructor(version: String) : this(io.github.z4kn4fein.semver.Version.parse(version, false))

    override fun compareTo(other: Version): Int = if (other is SemanticVersion) {
        this.delegate.compareTo(other.delegate)
    } else {
        this.toString().compareTo(other.toString())
    }

    override fun toString(): String = this.delegate.toString()
    override fun asSemVer(): io.github.z4kn4fein.semver.Version = this.delegate
}