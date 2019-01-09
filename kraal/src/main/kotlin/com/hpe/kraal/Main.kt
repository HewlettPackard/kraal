// (C) Copyright 2019 Hewlett Packard Enterprise Development LP
package com.hpe.kraal

import java.net.URLClassLoader
import java.nio.file.Paths

/**
 * Standalone driver application for Kraal.  Takes a list of files as input.  Any .jar or .class files are modified
 * **in place** to remove irreducible loops.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) usage()
    if (args.singleOrNull() in setOf("--help", "-h")) usage()

    val inputFiles = args.map { Paths.get(it) }
    val inputUrls = inputFiles.map { it.toUri().toURL() }.toTypedArray()
    val classloader = URLClassLoader(inputUrls)

    for (file in inputFiles) {
        val name = file.fileName.toString()
        if (name.endsWith(".jar") || name.endsWith(".class")) {
            removeIrreducibleLoops(file, classloader)
        }
    }
}

private fun usage(): Nothing {
    // throw an exception instead of exiting the JVM so that it works better with Maven exec:java
    error("Standalone driver application for Kraal.  Takes a list of files as input.\n" +
            "Any .jar or .class files are modified **in place** to remove irreducible loops.\n\n" +
            "Usage: java com.hpe.kraal.MainKt <file>...")
}