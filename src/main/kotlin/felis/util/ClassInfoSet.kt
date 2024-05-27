package felis.util

class ClassInfoSet(private val provider: (String) -> ClassInfo) {
    data class ClassInfo(
        val name: String,
        val superName: String?,
        val interfaces: List<String>,
        val isInterface: Boolean
    )

    companion object {
        const val OBJECT = "java/lang/Object"
    }

    private val info: MutableMap<String, ClassInfo> = hashMapOf(
        OBJECT to ClassInfo(OBJECT, null, emptyList(), false)
    )
    private val cache: MutableMap<Pair<String, String>, String> = hashMapOf()

    operator fun get(name: String): ClassInfo = this.info.computeIfAbsent(name, this.provider)

    fun getCommonSuperClass(class1: String, class2: String): String = this.cache.computeIfAbsent(Pair(class1, class2)) {
        this.getCommonSuperClass(this[class1], this[class2]).name
    }

    private fun getCommonSuperClass(class1: ClassInfo, class2: ClassInfo): ClassInfo =
        if (class1.name == OBJECT || class2.name == OBJECT) {
            this[OBJECT]
            // isAssignableFrom = class1 = class2;
        } else if (this.canAssign(class1, class2)) {
            class1
        } else if (this.canAssign(class2, class1)) {
            class2
        } else if (class1.isInterface || class2.isInterface) {
            this[OBJECT]
        } else {
            this.getCommonSuperClass(class1, class2.superName?.let(this::get) ?: this[OBJECT])
        }

    private fun canAssign(superType: ClassInfo, subType: ClassInfo?): Boolean {
        if (subType == null) return false
        if (!superType.isInterface) {
            var innerSubtype = subType
            while (innerSubtype != null) {
                if (superType.name == innerSubtype.name || superType.name == innerSubtype.superName) return true
                if (innerSubtype.name == OBJECT) return false
                innerSubtype = innerSubtype.superName?.let(this::get)
            }
            return false
        }
        return checkImplements(subType, subType.name)
    }

    private fun checkImplements(clazz: ClassInfo, iface: String): Boolean =
        if (clazz.name == OBJECT) {
            false
        } else if (clazz.interfaces.any { iface == it || this.checkImplements(this[it], it) }) {
            true
        } else {
            !clazz.isInterface && this.checkImplements(clazz.superName?.let(this::get) ?: this[OBJECT], iface)
        }
}