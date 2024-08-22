package felis.launcher

import felis.meta.Version

sealed class MinecraftVersion(val version: String) : Version {
    companion object {
        fun parse(version: String): MinecraftVersion =
            when (listOf("a", "b", "rd", "c", "inf").find(version::startsWith)) {
                "a", "rd", "c", "inf" -> Alpha(version)
                "b" -> Beta(version)
                else -> {
                    if (version.contains(".")) {
                        if (version.contains("rc")) {
                            ReleaseCandidate(version)
                        } else if (version.contains("pre")) {
                            PreRelease(version)
                        } else {
                            Release(version)
                        }
                    } else {
                        Snapshot(version)
                    }
                }
            }
    }

    class Alpha(version: String) : MinecraftVersion(version) {
        companion object {
            val weirdVersions = listOf(
                "rd-132211",
                "rd-132328",
                "rd-20090515",
                "rd-160052",
                "rd-161348",
                "c0.0.11a",
                "c0.0.13a_03",
                "c0.0.13a",
                "c0.30_01c",
                "inf-20100618"
            )
        }

        override fun compareTo(other: MinecraftVersion): Int =
            if (other !is Alpha) -1 else {
                if (this.version.startsWith("a")) {
                    if (other.version.startsWith("a")) {
                        this.version.compareTo(other.version)
                    } else {
                        1
                    }
                } else {
                    if (other.version.startsWith("a")) {
                        -1
                    } else {
                        val thisIdx = weirdVersions.indexOf(this.version)
                        val otherIdx = weirdVersions.indexOf(other.version)
                        thisIdx - otherIdx
                    }
                }
            }

        override fun asSemVer(): io.github.z4kn4fein.semver.Version =
            io.github.z4kn4fein.semver.Version.parse("0.0.0-alpha${this.version}")
    }

    class Beta(version: String) : MinecraftVersion(version) {
        override fun compareTo(other: MinecraftVersion): Int = when (other) {
            is Alpha -> 1
            is Beta -> this.version.compareTo(other.version)
            else -> -1
        }

        override fun asSemVer(): io.github.z4kn4fein.semver.Version =
            io.github.z4kn4fein.semver.Version.parse("0.0.0-beta${this.version}")
    }

    class Release(version: String) : MinecraftVersion(version) {
        // releases, prereleases, and release candidates can be handled by semver
        private val semVer = io.github.z4kn4fein.semver.Version.parse(this.version, false)
        override fun compareTo(other: MinecraftVersion): Int = when (other) {
            is Release -> this.semVer.compareTo(other.semVer)
            is Alpha, is Beta -> 1
            is ExperimentalVersion -> this.compareTo(other.release)
        }

        override fun asSemVer(): io.github.z4kn4fein.semver.Version = this.semVer
    }

    abstract class ExperimentalVersion(version: String) : MinecraftVersion(version) {
        abstract val release: Release
    }

    class Snapshot(version: String) : ExperimentalVersion(version) {
        val year = version.substring(0..1).toInt()
        val week = version.substring(3..4).toInt()
        val revision = version.substring(5)

        // yoinked from fabric loader
        override val release = Release(
            if (year >= 24 && week >= 33) {
                "1.21.2"
            } else if (year == 24 && week >= 18 && week <= 21) {
                "1.21"
            } else if (year == 23 && week >= 51 || year == 24 && week <= 14) {
                "1.20.5"
            } else if (year == 23 && week >= 40 && week <= 46) {
                "1.20.3"
            } else if (year == 23 && week >= 31 && week <= 35) {
                "1.20.2"
            } else if (year == 23 && week >= 12 && week <= 18) {
                "1.20"
            } else if (year == 23 && week <= 7) {
                "1.19.4"
            } else if (year == 22 && week >= 42) {
                "1.19.3"
            } else if (year == 22 && week == 24) {
                "1.19.1"
            } else if (year == 22 && week >= 11 && week <= 19) {
                "1.19"
            } else if (year == 22 && week >= 3 && week <= 7) {
                "1.18.2"
            } else if (year == 21 && week >= 37 && week <= 44) {
                "1.18"
            } else if (year == 20 && week >= 45 || year == 21 && week <= 20) {
                "1.17"
            } else if (year == 20 && week >= 27 && week <= 30) {
                "1.16.2"
            } else if (year == 20 && week >= 6 && week <= 22) {
                "1.16"
            } else if (year == 19 && week >= 34) {
                "1.15"
            } else if (year == 18 && week >= 43 || year == 19 && week <= 14) {
                "1.14"
            } else if (year == 18 && week >= 30 && week <= 33) {
                "1.13.1"
            } else if (year == 17 && week >= 43 || year == 18 && week <= 22) {
                "1.13"
            } else if (year == 17 && week == 31) {
                "1.12.1"
            } else if (year == 17 && week >= 6 && week <= 18) {
                "1.12"
            } else if (year == 16 && week == 50) {
                "1.11.1"
            } else if (year == 16 && week >= 32 && week <= 44) {
                "1.11"
            } else if (year == 16 && week >= 20 && week <= 21) {
                "1.10"
            } else if (year == 16 && week >= 14 && week <= 15) {
                "1.9.3"
            } else if (year == 15 && week >= 31 || year == 16 && week <= 7) {
                "1.9"
            } else if (year == 14 && week >= 2 && week <= 34) {
                "1.8"
            } else if (year == 13 && week >= 47 && week <= 49) {
                "1.7.3"
            } else if (year == 13 && week >= 36 && week <= 43) {
                "1.7"
            } else if (year == 13 && week >= 16 && week <= 26) {
                "1.6"
            } else if (year == 13 && week >= 11 && week <= 12) {
                "1.5.1"
            } else if (year == 13 && week >= 1 && week <= 10) {
                "1.5"
            } else if (year == 12 && week >= 49 && week <= 50) {
                "1.4.6"
            } else if (year == 12 && week >= 32 && week <= 42) {
                "1.4"
            } else if (year == 12 && week >= 15 && week <= 30) {
                "1.3"
            } else if (year == 12 && week >= 3 && week <= 8) {
                "1.2"
            } else if (year == 11 && week >= 47 || year == 12 && week <= 1) {
                "1.1"
            } else throw IllegalStateException("Invalid version ${this.version}")
        )

        override fun compareTo(other: MinecraftVersion): Int = when (other) {
            is Alpha, is Beta -> 1
            is Release -> this.release.compareTo(other)
            is Snapshot -> if (this.year < other.year) {
                -1
            } else if (this.year == other.year && this.week < other.week) {
                -1
            } else if (this.year == other.year && this.week == other.week && this.revision < other.revision) {
                -1
            } else if (this.year == other.year && this.week == other.week && this.revision == other.revision) {
                0
            } else {
                1
            }

            is ExperimentalVersion -> if (this.release == other.release) -1 else this.release.compareTo(other.release)
        }

        override fun asSemVer(): io.github.z4kn4fein.semver.Version =
            io.github.z4kn4fein.semver.Version.parse(this.release.asSemVer().toString() + "-${this.version}")
    }

    class PreRelease(version: String) : ExperimentalVersion(version) {
        override val release: Release = Release(this.version.split("-").first())
        val number = version.substringAfterLast("pre").toInt()
        override fun compareTo(other: MinecraftVersion): Int = when (other) {
            is Alpha, is Beta -> -1
            is Release -> this.release.compareTo(other)
            is ReleaseCandidate -> if (this.release == other.release) -1 else this.release.compareTo(other.release)
            is PreRelease -> this.number - other.number
            is Snapshot -> if (this.release == other.release) 1 else this.release.compareTo(other.release)
            else -> throw IllegalStateException("Cannot handle type ${other.javaClass}")
        }

        override fun asSemVer(): io.github.z4kn4fein.semver.Version =
            io.github.z4kn4fein.semver.Version.parse(this.version)
    }

    class ReleaseCandidate(version: String) : ExperimentalVersion(version) {
        override val release: Release = Release(this.version.split("-").first())
        val revision = version.substringAfterLast("rc").toInt()
        override fun compareTo(other: MinecraftVersion): Int = when (other) {
            is Alpha, is Beta -> -1
            is Release -> this.release.compareTo(other)
            is PreRelease -> if (this.release == other.release) 1 else this.release.compareTo(other.release)
            is ReleaseCandidate -> this.revision - other.revision
            is Snapshot -> if (this.release == other.release) 1 else this.release.compareTo(other.release)
            else -> throw IllegalStateException("Cannot handle type ${other.javaClass}")
        }

        override fun asSemVer(): io.github.z4kn4fein.semver.Version = this.release.asSemVer()
    }

    override fun compareTo(other: Version): Int =
        if (other is MinecraftVersion) this.compareTo(other) else throw IllegalArgumentException("Only minecraft versions can be compared with other minecraft versions")

    abstract fun compareTo(other: MinecraftVersion): Int

    override fun toString(): String = this.version
}