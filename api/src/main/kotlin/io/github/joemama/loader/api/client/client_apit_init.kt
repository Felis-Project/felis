@file:JvmName("ClientApiInit")

package io.github.joemama.loader.api.client

import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.asm.openMethod
import io.github.joemama.loader.transformer.ClassContainer
import io.github.joemama.loader.transformer.Transformation
import net.minecraft.client.Options
import net.minecraft.client.main.GameConfig
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode

interface ClientEntrypoint {
    fun onClientInit()
}

@Suppress("unused")
fun clientApiInit() {
    ModLoader.callEntrypoint("client", ClientEntrypoint::onClientInit)
}

class MinecraftTransformation : Transformation {
    override fun transform(container: ClassContainer) {
        container.openMethod(
            "<init>",
            Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(GameConfig::class.java))
        ) {
            val gameOptionsSet = instructions.first {
                it.opcode == Opcodes.PUTFIELD && (it as FieldInsnNode).let { field ->
                    field.name == "options" && field.desc == Type.getDescriptor(
                        Options::class.java
                    ) && field.owner == "net/minecraft/client/Minecraft"
                }
            }
            val methodCall = MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "io/github/joemama/loader/api/client/ClientApiInit",
                "clientApiInit",
                Type.getMethodDescriptor(Type.VOID_TYPE)
            )

            instructions.insert(gameOptionsSet, methodCall)
        }
    }
}