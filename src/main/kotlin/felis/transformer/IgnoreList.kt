package felis.transformer

class IgnoreList {
    private val packages: MutableList<Package> = mutableListOf()
    private val classes: MutableList<String> = mutableListOf()

    data class Package(val name: String, val absolute: Boolean) {
        fun matches(name: String): Boolean {
            return if (this.absolute) {
                name.substringBeforeLast('.') == this.name
            } else {
                name.startsWith(this.name)
            }
        }
    }

    fun isIgnored(name: String): Boolean = this.packages.any { it.matches(name) } || name in classes

    fun ignorePackage(packageName: String): IgnoreList {
        this.packages.add(Package(packageName, false))
        return this
    }

    fun ignorePackageAbsolute(packageName: String): IgnoreList {
        this.packages.add(Package(packageName, true))
        return this
    }

    fun ignoreClass(name: String) = this.classes.add(name)
}