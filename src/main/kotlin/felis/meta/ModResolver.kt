package felis.meta

import io.github.z4kn4fein.semver.Version
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ModResolver {
    private val graph: MutableMap<ModInstance, Mod> = hashMapOf()
    private val logger: Logger = LoggerFactory.getLogger(ModResolver::class.java)

    private var stage = 0

    data class ModInstance(val modid: Modid, val version: Version)
    class ModResolutionError(msg: String) : ModDiscoveryError(msg)

    fun record(mod: Mod) {
        val instance = ModInstance(mod.modid, mod.version)
        if (instance in this.graph) return

        this.graph[ModInstance(mod.modid, mod.version)] = mod
    }

    fun resolve(oldSet: ModSet?): ModSet {
        this.stage++
        this.logger.info("Stage $stage of resolution: resolving ${this.graph.size} mods")
        // resolve requirements
        val modidToVersion: MutableMap<Modid, Set<Version>> = this.graph.keys.groupBy(ModInstance::modid)
            .mapValues { it.value.map(ModInstance::version).toHashSet() }
            .toMutableMap()

        // find leaves (aka mods that other mods don't depend on at all)
        // Leaves are the ones that start the resolution process
        val leaves = this.graph.filter { leafCand ->
            !this.graph.any { (_, mod) ->
                mod.dependencies?.requires?.map { it.modid }?.contains(leafCand.key.modid) ?: false
            }
        }

        if (leaves.isEmpty())
            throw ModResolutionError("No leaf mods found. This means that some mods have a circular dependency on one another and thus it's a good idea to check which ones they are.")

        // check dependencies
        for (leaf in leaves.keys) {
            val versions = modidToVersion.getValue(leaf.modid)
            if (versions.size != 1)
                throw ModResolutionError(
                    "Multiple leaf versions: \"${leaf.modid}\" has multiple available versions($versions) with no way to specify which one is to be used"
                )
            this.resolve(leaf, modidToVersion)
        }

        // always pick the newest valid version
        val modSetCandidate = modidToVersion
            .mapValues { it.value.sortedDescending().take(1) }
            .mapValues { it.value.single() }
            .map { ModInstance(it.key, it.value) }

        // check breaks
        for (mod in modSetCandidate) {
            for (breakingMod in this.graph[mod]?.dependencies?.breaks ?: emptyList()) {
                if (breakingMod.modid == mod.modid) throw ModResolutionError("Mod \"${mod.modid}\" breaks on itself")

                for (breakCand in modSetCandidate) {
                    if (breakCand.modid == breakingMod.modid && breakingMod.version.isSatisfiedBy(breakCand.version))
                        throw ModResolutionError("Mod \"$mod\" breaks with version ${breakingMod.version} of mod \"${breakingMod.modid}\", and version ${breakCand.version} was provided")
                }
            }
        }

        val newSet = modSetCandidate
            .mapNotNull { this.graph[it] }
            .let { ModSet(it) }

        if (oldSet == null) return newSet

        for (mod in newSet) {
            val old = oldSet[mod.modid]
            if (old != null && old.version != mod.version) {
                this.logger.warn("Mod \"${mod.modid}\" was replaced after stage $stage of resolution(version: ${old.version} -> ${mod.version})")
            }
        }

        return newSet
    }

    private fun resolve(mod: ModInstance, modidToVersion: MutableMap<Modid, Set<Version>>) {
        val dependencies = this.graph[mod]?.dependencies ?: return
        if (dependencies.requires.isEmpty() && dependencies.breaks.isEmpty()) return

        // check that all dependencies exist
        for (dep in dependencies.requires) {
            if (dep.modid == mod.modid) throw ModResolutionError("Mod \"${mod.modid}\" depends on itself")

            // check a mod with that id is provided
            if (dep.modid !in modidToVersion)
                throw ModResolutionError("Mod \"${mod.modid}\" requires version \"${dep.version}\" of \"${dep.modid}\"")

            val versions = modidToVersion.getValue(dep.modid)
            val validVersions = mutableSetOf<Version>()
            // find all compatible versions
            for (v in versions) {
                if (dep.version.isSatisfiedBy(v)) validVersions += v
            }

            // if no valid versions exist we fail
            if (validVersions.isEmpty())
                throw ModResolutionError("Mod \"${mod.modid}\" requires version \"${dep.version}\" of \"${dep.modid}\", but only $versions are available.")

            // the new valid version replaces the old one
            modidToVersion[dep.modid] = validVersions.toHashSet()

            // if the dependency is fully reduced, then we can resolve its dependencies as well
            if (validVersions.size == 1) this.resolve(ModInstance(dep.modid, validVersions.single()), modidToVersion)
        }
    }
}