package io.github.joemama.loader.side

import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.transformer.ClassData
import io.github.joemama.loader.transformer.Transformation
import org.objectweb.asm.Type

object SideStrippingTransformation : Transformation {
    override fun transform(classData: ClassData) {
        classData.node.visibleAnnotations?.find { it.desc == Type.getDescriptor(OnlyIn::class.java) }?.let {
            @Suppress("UNCHECKED_CAST")
            val value = (it.values[1] as Array<String>)[1]
            val side = enumValueOf<Side>(value)
            if (side != ModLoader.side) classData.skip = true
        }

        if (classData.skip) return

        classData.node.methods.removeIf { method ->
            method.visibleAnnotations?.find { it.desc == Type.getDescriptor(OnlyIn::class.java) }?.let {
                @Suppress("UNCHECKED_CAST")
                val value = (it.values[1] as Array<String>)[1]
                val side = enumValueOf<Side>(value)
                side != ModLoader.side
            } ?: false
        }

        classData.node.fields.removeIf { field ->
            field.visibleAnnotations?.find { it.desc == Type.getDescriptor(OnlyIn::class.java) }?.let {
                @Suppress("UNCHECKED_CAST")
                val value = (it.values[1] as Array<String>)[1]
                val side = enumValueOf<Side>(value)
                side != ModLoader.side
            } ?: false
        }
    }
}