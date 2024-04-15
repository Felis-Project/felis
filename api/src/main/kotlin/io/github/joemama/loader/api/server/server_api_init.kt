@file:JvmName("ServerApiInit")

package io.github.joemama.loader.api.server

import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.asm.openMethod
import io.github.joemama.loader.side.OnlyIn
import io.github.joemama.loader.side.Side
import io.github.joemama.loader.transformer.ClassContainer
import io.github.joemama.loader.transformer.Transformation
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodInsnNode


@OnlyIn(Side.CLIENT)
interface ServerEntrypoint {
    fun onClientInit()
}

@Suppress("unused")
@OnlyIn(Side.CLIENT)
fun serverApiInit() {
    ModLoader.callEntrypoint("server", ServerEntrypoint::onClientInit)
}

object MainTransformation : Transformation {
    override fun transform(container: ClassContainer) {
        container.openMethod(
            "main",
            "([Ljava/lang/String;)V"
        ) {
            val hasAgreedToEulaCall = instructions.first {
                it is MethodInsnNode &&
                        it.opcode == Opcodes.INVOKEVIRTUAL &&
                        it.name == "hasAgreedToEULA" &&
                        it.owner == "net/minecraft/server/Eula" &&
                        it.desc == Type.getMethodDescriptor(Type.BOOLEAN_TYPE)
            }
            val methodCall = MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "io/github/joemama/loader/api/server/ServerApiInit",
                "serverApiInit",
                "()V"
            )

            instructions.insertBefore(hasAgreedToEulaCall, methodCall)
        }
    }
}