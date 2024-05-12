package felis.meta

sealed interface ModDiscoveryResult {
    class Success(override val mods: List<Mod>) : Mods
    class PartialMods(override val mods: List<Mod>, override val failedMods: List<ModDiscoveryError>) : Mods, Error
    class Failure(override val failedMods: List<ModDiscoveryError>) : Error

    interface Error : ModDiscoveryResult {
        val failedMods: List<ModDiscoveryError>
    }

    interface Mods : ModDiscoveryResult {
        val mods: List<Mod>
    }

    data object NoMods : ModDiscoveryResult
}