package io.github.joemama.loader.asm

import io.github.joemama.loader.transformer.ClassRef
import org.objectweb.asm.tree.MethodNode

fun ClassRef.openMethod(name: String, desc: String, action: MethodNode.() -> Unit) {
    this.node.methods.first { it.name == name && it.desc == desc }.action()
}
