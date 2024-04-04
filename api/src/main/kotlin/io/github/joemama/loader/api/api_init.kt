@file:JvmName("ApiInit")

package io.github.joemama.loader.api

import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.transformer.ClassData
import io.github.joemama.loader.transformer.Transformation
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface CommonEntrypoint {
    fun onInit()
}

val logger: Logger = LoggerFactory.getLogger("Loader API")

@Suppress("unused")
fun apiInit() {
    ModLoader.callEntrypoint("common", CommonEntrypoint::onInit)
}

class BootstrapTransformation : Transformation {
    override fun transform(classData: ClassData) {
        val clazz = classData.node
        val mn = clazz.methods.first { it.name == "bootStrap" && it.desc == "()V" }
        val insn = mn.instructions.first { insn ->
            if (insn.type != AbstractInsnNode.METHOD_INSN) {
                false
            } else {
                val mIns = insn as MethodInsnNode
                mIns.owner == "net/minecraft/core/registries/BuiltInRegistries" && mIns.name == "bootStrap" && mIns.desc == "()V"
            }
        }
        val methodCall = MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "io/github/joemama/loader/api/ApiInit",
            "apiInit",
            Type.getMethodDescriptor(Type.VOID_TYPE)
        )
        mn.instructions.insertBefore(insn, methodCall)
    }
}
