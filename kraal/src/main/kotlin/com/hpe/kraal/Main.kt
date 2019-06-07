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
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Standalone driver application for Kraal.  Takes a list of files as input.  Any .jar or .class files are modified
 * **in place** to remove irreducible loops.
 */
fun main(args: Array<String>) {
    val cli = CmdArgs(args).also(CmdArgs::parse)

    val excludeList = arrayListOf<String>()
    // Packages to exclude
    cli.findArg("--excludePackages")?.split(",")?.forEach {
        excludeList.add(it.replace(".", "/"))
    }
    // Classes to exclude
    cli.findArg("--excludeClasses")?.split(",")?.forEach {
        excludeList.add(it)
    }

    val inputFiles = cli.fileList.map { Paths.get(it) }
    val outputFiles = mutableListOf<Path?>()

    val inputUrls = inputFiles.map { it.toUri().toURL() }.toTypedArray()

    try {
        URLClassLoader(inputUrls).use { classloader ->
            for (file in inputFiles) {
                outputFiles.add(processFile(file, classloader, excludeList))
            }
        }
        updateFiles(inputFiles, outputFiles)
    } finally {
        for (temp in outputFiles) if (temp != null) {
            Files.deleteIfExists(temp)
        }
    }
}

/**
 * Process command line args. Possibly kotlinx.cli might make more sense when there are more args
 */
private data class CmdArgs(val args: Array<String>) {
    // List of .jar and .class files
    var fileList = listOf<String>()

    // Filter out any param starts with a '-'. Display help text if no args
    fun parse() {
        if (args.isEmpty()) usage()
        if (args.singleOrNull() in setOf("--help", "-h")) usage()
        fileList = args.filter { !it.startsWith("-") }
    }

    // If arg is found, return respective params
    fun findArg(arg: String): String? {
        args.forEach {
            if (it.startsWith(arg)) {
                return it.substring(arg.length + 1)
            }
        }
        return null
    }
}

private fun usage(): Nothing {
    // throw an exception instead of exiting the JVM so that it works better with Maven exec:java
    error("""Standalone driver application for Kraal.  Takes a list of files as input.
Any .jar or .class files are modified **in place** to remove irreducible loops.

Usage: java com.hpe.kraal.MainKt <file>...
  --excludePackages="org.pkg1,org.pkg2"     comma separated list of packages to exclude
  --excludeClasses="class1,class2"          comma separated list of classes to exclude. ".class" is redundant
  -h, --help                                display this help text and exit
        """)
}

private fun processFile(
    file: Path,
    classloader: ClassLoader,
    excludeList: ArrayList<String> = arrayListOf<String>()
): Path? {
    val name = file.fileName.toString()
    if (name.endsWith(".jar") || name.endsWith(".class")) {
        val output = Files.createTempFile(
            file.toAbsolutePath().parent,
            name,
            ".${file.fileName.toString().substringAfterLast('.', ".tmp")}"
        )
        try {
            Files.copy(file, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
            if (removeIrreducibleLoops(output, classloader, excludeList)) {
                return output
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Files.delete(output)
            throw e
        }
        Files.delete(output)
    }
    return null
}

private fun updateFiles(inputFiles: List<Path>, outputFiles: List<Path?>) {
    for ((file, output) in inputFiles.zip(outputFiles)) {
        if (output != null) {
            Files.move(output, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}