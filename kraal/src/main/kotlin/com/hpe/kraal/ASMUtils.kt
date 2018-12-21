/*
 * Copyright 2018-2019 Hewlett Packard Enterprise Development LP
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.hpe.kraal

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Specialization of ASM ClassWriter that uses the given [classloader] and to load related classes (e.g. supertypes) and
 * recomputes the frames and maximum stack and number of locals in methods written (see [ClassWriter.COMPUTE_FRAMES]).
 */
private class CustomClassloaderClassWriter(private val classloader: ClassLoader) :
    ClassWriter(ClassWriter.COMPUTE_FRAMES) {
    override fun getClassLoader() = classloader
}

/**
 * Read from the receiving ClassReader into a ClassNode.  Uses [ClassReader.SKIP_FRAMES].
 */
internal fun ClassReader.toClassNode(): ClassNode = ClassNode().also { accept(it, ClassReader.SKIP_FRAMES) }

/**
 * Read a ClassNode from the given [classFile].
 */
internal fun readClassFile(classFile: Path): ClassNode = ClassReader(Files.readAllBytes(classFile)).toClassNode()

/**
 * Create a class writer for the receiving class node.  The given [classloader] must be able to load related types
 * (e.g. superclasses).
 */
internal fun ClassNode.toByteArray(classloader: ClassLoader = Thread.currentThread().contextClassLoader): ByteArray {
    val classWriter = CustomClassloaderClassWriter(classloader).also { accept(it) }
    val stringWriter = StringWriter()
    CheckClassAdapter.verify(ClassReader(classWriter.toByteArray()), classloader, false, PrintWriter(stringWriter))
    check(stringWriter.toString().isEmpty()) {
        "Found errors in modified bytecode for $name: $stringWriter"
    }
    return classWriter.toByteArray()
}

/**
 * Write the receiving ASM ClassNode to a class [file].  The given [classloader] must be able to load related types
 * (e.g. superclasses).
 */
internal fun ClassNode.writeToFile(file: Path, classloader: ClassLoader = Thread.currentThread().contextClassLoader) {
    Files.write(file, toByteArray(classloader), StandardOpenOption.TRUNCATE_EXISTING)
}