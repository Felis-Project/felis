package felis.meta

class LexVersion(val version: String) : Version {
    override fun compareTo(other: Version): Int = this.version.compareTo(other.toString())
    override fun toString(): String = this.version
    override fun asSemVer(): io.github.z4kn4fein.semver.Version? = null
}