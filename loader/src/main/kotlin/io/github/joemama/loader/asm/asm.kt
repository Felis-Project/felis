package io.github.joemama.loader.asm

import io.github.joemama.loader.transformer.ClassContainer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.*
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

open class AsmException(msg: String) : Exception(msg)
class MethodNotFoundException(name: String, clazz: String) :
    AsmException("Method $name could not be found in target $clazz")

inline fun ClassContainer.openMethod(name: String, desc: String, action: MethodScope.() -> Unit) {
    val method = this.node.methods.find { it.name == name && it.desc == desc }
    if (method == null) throw MethodNotFoundException(name, this.name)
    action(MethodScope(method, this.internalName))
}

sealed interface InjectionPoint {
    companion object {
        private val logger = LoggerFactory.getLogger(InjectionPoint::class.java)
    }

    fun inject(scope: MethodScope, insns: InsnList, origin: InsnList)

    data object Head : InjectionPoint {
        override fun inject(scope: MethodScope, insns: InsnList, origin: InsnList) = origin.insert(insns)
    }

    data object Tail : InjectionPoint {
        override fun inject(scope: MethodScope, insns: InsnList, origin: InsnList) =
            origin.last.let { origin.insert(it, insns) }
    }

    data object Return : InjectionPoint {
        override fun inject(scope: MethodScope, insns: InsnList, origin: InsnList) =
            origin.filter { (Opcodes.IRETURN..Opcodes.RETURN).contains(it.opcode) }.forEach {
                origin.insertBefore(it, insns)
            }
    }

    data class Invoke(val owner: Type, val method: Method, val limit: Int? = null) : InjectionPoint {
        constructor(
            owner: Type,
            name: String,
            returnType: Type = Type.VOID_TYPE,
            vararg params: Type,
            limit: Int? = null,
        ) : this(
            owner,
            Method(name, Type.getMethodDescriptor(returnType, *params)),
            limit
        )

        constructor(
            owner: String,
            name: String,
            returnType: Type = Type.VOID_TYPE,
            vararg params: Type,
            limit: Int? = null,
        ) : this(
            Type.getObjectType(owner),
            Method(name, Type.getMethodDescriptor(returnType, *params)),
            limit
        )

        override fun inject(scope: MethodScope, insns: InsnList, origin: InsnList) {
            var invokes = origin.filter { it.type == AbstractInsnNode.METHOD_INSN }
                .map { it as MethodInsnNode }
                .filter { it.owner == this.owner.internalName && it.desc == this.method.descriptor }
            if (invokes.isEmpty()) logger.error("Could not locate injection point at $owner for call to $method")
            if (this.limit != null) {
                invokes = invokes.take(this.limit)
            }

            for (invokation in invokes) {
                origin.insertBefore(invokation, insns)
            }
        }
    }
}

typealias InternalName = String

interface LocateScope {
    fun locate(owner: String): InternalName = owner.replace(".", "/")
    fun locate(owner: Class<*>): InternalName = this.locate(Type.getType(owner))
    fun locate(owner: Type): InternalName = owner.internalName
}

interface TypeScope {
    fun typeOf(owner: String): Type = Type.getObjectType(owner.replace(".", "/"))
    fun typeOf(owner: KClass<*>): Type = Type.getType(owner.java)
}

const val ACCESSNUKE = (Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED).inv()

enum class AccessModifier(val code: Int) {
    PUBLIC(Opcodes.ACC_PUBLIC),
    PRIVATE(Opcodes.ACC_PRIVATE),
    PROTECTED(Opcodes.ACC_PROTECTED),
    PACKAGE_PRIVATE(0)
}

class MethodScope(val node: MethodNode, val owner: String) : TypeScope {
    @Suppress("NOTHING_TO_INLINE")
    inline fun access(access: AccessModifier) {
        this.node.access = this.node.access and ACCESSNUKE or access.code
    }

    inline fun inject(at: InjectionPoint, config: InsnScope.() -> Unit) {
        val scope = InsnScope()
        config(scope)
        at.inject(this, scope.insns, this.node.instructions)
    }
}

class InsnScope : LocateScope, TypeScope {
    val insns = InsnList()

    fun invokeStatic(owner: InternalName, name: String, returnType: Type = Type.VOID_TYPE, vararg params: Type) =
        this.invokeMethod(owner, name, Opcodes.INVOKESTATIC, returnType, *params)

    fun invokeVirtual(owner: InternalName, name: String, returnType: Type = Type.VOID_TYPE, vararg params: Type) =
        this.invokeMethod(owner, name, Opcodes.INVOKEVIRTUAL, returnType, *params)

    fun invokeSpecial(owner: InternalName, name: String, returnType: Type = Type.VOID_TYPE, vararg params: Type) =
        this.invokeMethod(owner, name, Opcodes.INVOKESPECIAL, returnType, *params)

    fun invokeInterface(owner: InternalName, name: String, returnType: Type = Type.VOID_TYPE, vararg params: Type) =
        this.invokeMethod(owner, name, Opcodes.INVOKEINTERFACE, returnType, *params)

    private fun invokeMethod(
        owner: InternalName,
        name: String,
        opcode: Int,
        returnType: Type = Type.VOID_TYPE,
        vararg params: Type
    ) = this.insns.add(MethodInsnNode(opcode, owner, name, Type.getMethodDescriptor(returnType, *params)))

    private fun local(code: Int, local: Int) = this.insns.add(VarInsnNode(code, local))

    fun istore(local: Int) = this.local(local, Opcodes.ISTORE)
    fun lstore(local: Int) = this.local(local, Opcodes.LSTORE)
    fun fstore(local: Int) = this.local(local, Opcodes.FSTORE)
    fun dstore(local: Int) = this.local(local, Opcodes.DSTORE)
    fun astore(local: Int) = this.local(local, Opcodes.ASTORE)

    fun iload(local: Int) = this.local(local, Opcodes.ILOAD)
    fun lload(local: Int) = this.local(local, Opcodes.LLOAD)
    fun fload(local: Int) = this.local(local, Opcodes.FLOAD)
    fun dload(local: Int) = this.local(local, Opcodes.DLOAD)
    fun aload(local: Int) = this.local(local, Opcodes.ALOAD)

    private fun field(code: Int, owner: InternalName, name: String, type: Type) =
        this.insns.add(FieldInsnNode(code, owner, name, type.descriptor))

    fun getStatic(owner: InternalName, name: String, type: Type) = field(Opcodes.GETSTATIC, owner, name, type)
    fun putStatic(owner: InternalName, name: String, type: Type) = field(Opcodes.PUTSTATIC, owner, name, type)
    fun getField(owner: InternalName, name: String, type: Type) = field(Opcodes.GETFIELD, owner, name, type)
    fun putField(owner: InternalName, name: String, type: Type) = field(Opcodes.PUTFIELD, owner, name, type)

    fun add(node: AbstractInsnNode) = this.insns.add(node)

    fun ldc(value: Any) = this.insns.add(LdcInsnNode(value))
}