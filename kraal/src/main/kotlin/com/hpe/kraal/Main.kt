/*
 * Copyright 2019 Hewlett Packard Enterprise Development LP
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

import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Standalone driver application for Kraal.  Takes a list of files as input.  Any .jar or .class files are modified
 * **in place** to remove irreducible loops.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) usage()
    if (args.singleOrNull() in setOf("--help", "-h")) usage()

    val inputFiles = args.map { Paths.get(it) }

    val outputFiles = inputFiles.map { file ->
        Files.createTempFile(
            file.toAbsolutePath().parent,
            file.fileName.toString(),
            ".${file.fileName.toString().substringAfterLast('.', ".tmp")}"
        )
    }

    val inputUrls = inputFiles.map { it.toUri().toURL() }.toTypedArray()
    val classloader = URLClassLoader(inputUrls)

    try {
        classloader.use {
            for ((file, output) in inputFiles.zip(outputFiles)) {
                val name = file.fileName.toString()
                if (name.endsWith(".jar") || name.endsWith(".class")) {
                    Files.copy(file, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                    removeIrreducibleLoops(output, classloader)
                } else {
                    Files.delete(output)
                }
            }
        }
        for ((file, output) in inputFiles.zip(outputFiles)) {
            Files.move(output, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        for (file in outputFiles) {
            Files.deleteIfExists(file)
        }
    }
}

private fun usage(): Nothing {
    // throw an exception instead of exiting the JVM so that it works better with Maven exec:java
    error(
        "Standalone driver application for Kraal.  Takes a list of files as input.\n" +
                "Any .jar or .class files are modified **in place** to remove irreducible loops.\n\n" +
                "Usage: java com.hpe.kraal.MainKt <file>..."
    )
}