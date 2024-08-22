package felis.meta

import kotlinx.serialization.Serializable

@Serializable(with = VersionSerializer::class)
interface Version : Comparable<Version> {
    override fun toString(): String
    fun asSemVer(): io.github.z4kn4fein.semver.Version?
}