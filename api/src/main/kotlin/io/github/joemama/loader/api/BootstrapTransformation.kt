package io.github.joemama.loader.api

import io.github.joemama.loader.transformer.Transformation
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.slf4j.LoggerFactory

class BootstrapTransformation : Transformation {
    private val logger = LoggerFactory.getLogger(BootstrapTransformation::class.java)

    // ======================== Code from Bootstrap=======================
    // public static void bootStrap() {
    //     if (isBootstrapped) {
    //         return;
    //     }
    //     isBootstrapped = true;
    //     Instant $$0 = Instant.now();
    //     if (BuiltInRegistries.REGISTRY.keySet().isEmpty()) {
    //         throw new IllegalStateException("Unable to load registries");
    //     }
    //     FireBlock.bootStrap();
    //     ComposterBlock.bootStrap();
    //     if (EntityType.getKey(EntityType.PLAYER) == null) {
    //         throw new IllegalStateException("Failed loading EntityTypes");
    //     }
    //     PotionBrewing.bootStrap();
    //     EntitySelectorOptions.bootStrap();
    //     DispenseItemBehavior.bootStrap();
    //     CauldronInteraction.bootStrap();
    //     ================= Our Code ============================================
    //     ApiInitKt.apiInit();
    //     =======================================================================
    //     BuiltInRegistries.bootStrap();
    //     CreativeModeTabs.validate();
    //     Bootstrap.wrapStreams();
    //     bootstrapDuration.set(Duration.between((Temporal)$$0, (Temporal)Instant.now()).toMillis());
    // }
    override fun transform(clazz: ClassNode, name: String) {
        val mn = clazz.methods.first { it.name == "bootStrap" && it.desc == "()V" }
        val insn = mn.instructions.find { insn ->
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
        logger.debug("injected API main call")
    }
}
