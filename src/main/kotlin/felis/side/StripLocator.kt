package felis.side

import felis.ModLoader
import org.objectweb.asm.*
import org.objectweb.asm.commons.Method

class StripLocator : ClassVisitor(Opcodes.ASM9) {
    var skipEntire = false
    val methods = mutableListOf<Method>()
    val fields = mutableListOf<String>()
    override fun visitAnnotation(
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? = if (descriptor == Type.getDescriptor(OnlyIn::class.java)) {
        OnlyInDetector { this.skipEntire = true }
    } else super.visitAnnotation(descriptor, visible)

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor = object : MethodVisitor(this.api) {
        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? =
            if (descriptor == Type.getDescriptor(OnlyIn::class.java)) OnlyInDetector {
                this@StripLocator.methods += Method(name, descriptor)
            } else super.visitAnnotation(descriptor, visible)
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?
    ): FieldVisitor = object : FieldVisitor(this.api) {
        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? =
            if (descriptor == Type.getDescriptor(OnlyIn::class.java)) OnlyInDetector {
                this@StripLocator.fields += name
            } else super.visitAnnotation(descriptor, visible)
    }

    class OnlyInDetector(val action: () -> Unit) : AnnotationVisitor(Opcodes.ASM9) {
        override fun visitEnum(name: String, descriptor: String, value: String) {
            if (name == "side" && descriptor == Type.getDescriptor(Side::class.java)) {
                val side = enumValueOf<Side>(value)
                if (side != ModLoader.side) this.action()
            }
        }
    }
}