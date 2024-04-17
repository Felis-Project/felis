package io.github.joemama.loader.make

import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipException
import javax.inject.Inject

abstract class JarMerger {
    @get:Inject
    abstract val project: Project
    fun merge(client: File, server: File): File {
        val version = project.extensions.getByType(LoaderMakePlugin.Extension::class.java).version
        val clientJar = JarFile(client)
        val serverJar = JarFile(server)
        val output = client.parentFile.resolve("$version-mapped-merged.jar")

        if (!output.exists()) {
            println("Merging ${client.path} and ${server.path}")
            output.createNewFile()
            val jarWriter = JarOutputStream(output.outputStream())

            jarWriter.use {
                fromJar(jarWriter, clientJar, serverJar, "CLIENT")
                fromJar(jarWriter, serverJar, clientJar, "SERVER")
            }
        }

        return output
    }

    private fun fromJar(jarWriter: JarOutputStream, clientJar: JarFile, serverJar: JarFile, side: String) {
        fun writeEntry(originJar: JarFile, entry: JarEntry) {
            jarWriter.putNextEntry(entry)
            originJar.getInputStream(entry).use {
                jarWriter.write(it.readAllBytes())
            }
            jarWriter.closeEntry()
        }

        fun writeClass(name: String, node: ClassNode) {
            val entry = JarEntry(name)
            jarWriter.putNextEntry(entry)
            val writer = ClassWriter(0)
            node.accept(writer)
            jarWriter.write(writer.toByteArray())
            jarWriter.closeEntry()
        }

        for (entry in clientJar.entries()) {
            if (!entry.name.endsWith(".class")) {
                try {
                    writeEntry(clientJar, entry)
                } catch (_: ZipException) {
                } // ignore
                continue
            }

            val serverEntry = serverJar.getJarEntry(entry.name)
            if (serverEntry == null) {
                clientJar.getInputStream(entry).use { input ->
                    val reader = ClassReader(input)
                    val node = ClassNode().also { reader.accept(it, 0) }
                    if (node.visibleAnnotations == null) node.visibleAnnotations = mutableListOf()
                    node.visibleAnnotations.add(AnnotationNode("Lio/github/joemama/loader/side/OnlyIn;").also {
                        it.visitEnum("side", "Lio/github/joemama/loader/side/Side;", side)
                    })
                    writeClass(entry.name, node)
                }
                continue
            }

            val clientNode = ClassNode().also { ClassReader(clientJar.getInputStream(entry)).accept(it, 0) }
            val serverNode = ClassNode().also { ClassReader(serverJar.getInputStream(serverEntry)).accept(it, 0) }
            val result = ClassNode().apply {
                clientNode.accept(this)
            }
            result.interfaces.apply {
                serverNode.interfaces.filter { !this.contains(it) }.forEach(this::add)
            }
            result.fields.apply {
                serverNode.fields.filter { field ->
                    this.none { it.desc == field.desc && it.name == field.name }
                }.forEach { field ->
                    if (field.visibleAnnotations == null) field.visibleAnnotations = mutableListOf()
                    field.visibleAnnotations.add(AnnotationNode("Lio/github/joemama/loader/side/OnlyIn;").also {
                        it.visitEnum("side", "Lio/github/joemama/loader/side/Side;", side)
                    })

                    this.add(field)
                }
            }

            result.methods.apply {
                serverNode.methods.filter { method ->
                    this.none { it.desc == method.desc && it.name == method.name }
                }.forEach { method ->
                    if (method.visibleAnnotations == null) method.visibleAnnotations = mutableListOf()
                    method.visibleAnnotations.add(AnnotationNode("Lio/github/joemama/loader/side/OnlyIn;").also {
                        it.visitEnum("side", "Lio/github/joemama/loader/side/Side;", side)
                    })

                    this.add(method)
                }
            }

            try {
                writeClass(entry.name, result)
            } catch (_: ZipException) {
            } // ignored
        }
    }
}