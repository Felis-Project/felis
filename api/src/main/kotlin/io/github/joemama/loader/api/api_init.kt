@file:JvmName("ApiInit")

package io.github.joemama.loader.api

import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.asm.openMethod
import io.github.joemama.loader.transformer.ClassContainer
import io.github.joemama.loader.transformer.Transformation
import org.objectweb.asm.*
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
    override fun transform(container: ClassContainer) {
        container.openMethod("bootStrap", "()V") {
            val insn = instructions.first { insn ->
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
            instructions.insertBefore(insn, methodCall)
        }
    }
}
