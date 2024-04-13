package io.github.joemama.loader.asm

import io.github.joemama.loader.transformer.ClassContainer
import org.objectweb.asm.tree.MethodNode

open class AsmException(msg: String) : Exception(msg)
class MethodNotFoundException(name: String, clazz: String) :
    AsmException("Method $name could not be found in target $clazz")

fun ClassContainer.openMethod(name: String, desc: String, action: MethodNode.() -> Unit) {
    val method = this.node.methods.find { it.name == name && it.desc == desc }
    if (method == null) throw MethodNotFoundException(name, this.name)
    action(method)
}
