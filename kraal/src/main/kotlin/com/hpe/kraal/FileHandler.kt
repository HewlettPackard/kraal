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

import org.objectweb.asm.tree.ClassNode
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

// TODO: better file name?
private val log = LoggerFactory.getLogger("com.hpe.kraal.FileHandlerKt")

/**
 * Modify the [classNode] if necessary to remove irreducible loops in the instruction lists of each method.  Returns
 * true if the given class was modified.
 *
 * The bytecode that would result from writing the is not verified by ASM.  Use [ClassNode.toByteArray],
 * [ClassNode.writeToFile], or ASM's CheckClassAdapter directly to perform verification of the updated bytecode.
 */
fun removeIrreducibleLoops(classNode: ClassNode): Boolean {
    var modified = false
    for (method in classNode.methods) {
        modified = removeIrreducibleLoops(classNode, method) || modified
    }

    return modified
}

/**
 * Process the .jar or .class [file] to remove irreducible loops in the instruction lists of each method.  Class files
 * are overwritten if they contained irreducible loops, and the contents of jars are updated in-place.  Returns
 * true if the given file was modified.
 *
 * If the class has related types (e.g. supertypes) that cannot be loaded by the current thread's context classloader,
 * then a [classloader] that can must be specified.
 */
fun removeIrreducibleLoops(file: Path, classloader: ClassLoader = Thread.currentThread().contextClassLoader): Boolean {
    if (file.toString().endsWith(".class")) {
        return processClassFile(file, classloader)
    } else if (file.toString().endsWith(".jar")) {
        log.info { "Processing jar file $file" }

        FileSystems.newFileSystem(file, null).use { zipfs ->
            val classes = Files.walk(zipfs.getPath("/")).filter { entry ->
                entry.toString().endsWith(".class") && !Files.isDirectory(entry)
            }

            var modified = false
            for (classFile in classes) {
                modified = processClassFile(classFile, classloader) || modified
            }
            return modified
        }
    } else {
        log.debug { "Not processing input file $file" }
        return false
    }
}

private fun processClassFile(classFile: Path, classloader: ClassLoader): Boolean {
    log.info { "Processing $classFile" }
    val classNode = readClassFile(classFile)
    if (removeIrreducibleLoops(classNode)) {
        classNode.writeToFile(classFile, classloader)
        log.info { "Updated $classFile" }
        return true
    }
    return false
}
