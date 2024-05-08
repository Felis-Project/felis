package felis.side

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.Method

class ClassStripper(del: ClassVisitor, private val methods: List<Method>, private val fields: List<String>) :
    ClassVisitor(Opcodes.ASM9, del) {
    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor? = if (Method(name, descriptor) in this.methods) null else super.visitMethod(
        access,
        name,
        descriptor,
        signature,
        exceptions
    )

    override fun visitField(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        value: Any?
    ): FieldVisitor? = if (name in this.fields) null else super.visitField(access, name, descriptor, signature, value)
}
