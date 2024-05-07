package felis.meta

open class ModDiscoveryException(msg: String) : Exception(msg)
class ModMetaException(msg: String) : ModDiscoveryException(msg)
data object NotAMod : Throwable() {
    private fun readResolve(): Any = NotAMod
}

