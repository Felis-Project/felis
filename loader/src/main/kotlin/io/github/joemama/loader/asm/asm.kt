package io.github.joemama.loader.asm

import io.github.joemama.loader.transformer.ClassContainer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.slf4j.LoggerFactory

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

class MethodScope(val node: MethodNode, val owner: String) {
    inline fun inject(at: InjectionPoint, config: InsnScope.() -> Unit) {
        val scope = InsnScope()
        config(scope)
        at.inject(this, scope.insns, this.node.instructions)
    }
}

class InsnScope {
    val insns = InsnList()

    fun invokeStatic(owner: Type, name: String, returnType: Type = Type.VOID_TYPE, vararg params: Type) {
        this.insns.add(
            MethodInsnNode(
                Opcodes.INVOKESTATIC,
                owner.internalName,
                name,
                Type.getMethodDescriptor(returnType, *params)
            )
        )
    }

    fun invokeStatic(owner: String, name: String, returnType: Type = Type.VOID_TYPE, vararg params: Type) =
        this.invokeStatic(Type.getObjectType(owner), name, returnType, *params)

    fun invokeStatic(owner: Class<*>, name: String, returnType: Type = Type.VOID_TYPE, vararg params: Type) =
        this.invokeStatic(Type.getType(owner), name, returnType, *params)

    fun ldc(value: Any) = this.insns.add(LdcInsnNode(value))
}