package felis.util

class ClassInfoSet(private val unstableProvider: (String) -> ClassInfo) {
    companion object {
        const val OBJECT = "java/lang/Object"
    }

    private val info: MutableMap<String, ClassInfo> = hashMapOf(
        OBJECT to ClassInfo(OBJECT, null, emptyList(), false)
    )
    private val cache: MutableMap<Pair<String, String>, String> = hashMapOf()

    operator fun get(name: String): ClassInfo = this.info.computeIfAbsent(name, this.unstableProvider)

    fun getCommonSuperClass(class1: String, class2: String): String = this.cache.computeIfAbsent(Pair(class1, class2)) {
        this.getCommonSuperClass(this[class1], this[class2]).name
    }

    private fun getCommonSuperClass(class1: ClassInfo, class2: ClassInfo): ClassInfo {
        if (class1.name == OBJECT) {
            return class1
        }
        if (class2.name == OBJECT) {
            return class2
        }
        // isAssignableFrom = class1 = class2;
        if (this.canAssign(class1, class2)) {
            return class1
        }
        if (this.canAssign(class2, class1)) {
            return class2
        }
        if (class1.isInterface || class2.isInterface) {
            return this[OBJECT]
        }
        return this.getCommonSuperClass(class1, class2.superName?.let(this::get) ?: this[OBJECT])
    }

    private fun canAssign(superType: ClassInfo, subType: ClassInfo?): Boolean {
        if (subType == null) return false
        var innerSubtype = subType
        val name = superType.name
        if (superType.isInterface) {
            return isImplementingInterface(innerSubtype, name)
        } else {
            while (innerSubtype != null) {
                if (name == innerSubtype.name || name == innerSubtype.superName) {
                    return true
                }
                if (innerSubtype.name == OBJECT) {
                    return false
                }
                innerSubtype = innerSubtype.superName?.let(this::get)
            }
        }
        return false
    }

    private fun isImplementingInterface(clazz: ClassInfo, niddle: String): Boolean {
        if (clazz.name == OBJECT) {
            return false
        }
        for (iface in clazz.interfaces) {
            if (iface == niddle) {
                return true
            } else {
                if (this.isImplementingInterface(this[iface], niddle)) {
                    return true
                }
            }
        }
        if (clazz.isInterface) {
            return false
        }
        return this.isImplementingInterface(clazz.superName?.let(this::get) ?: this[OBJECT], niddle)
    }
}