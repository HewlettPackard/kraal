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
@file:Suppress("UnstableApiUsage")

package com.hpe.kraal.gradle

import com.hpe.kraal.debug
import com.hpe.kraal.removeIrreducibleLoops
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import javax.inject.Inject

/**
 * Gradle task to run Kraal on classes and jars to remove irreducible loops so that the code can then be used with
 * GraalVM.
 */
@CacheableTask
open class KraalTask @Inject constructor(private val exec: WorkerExecutor) : DefaultTask() {

    /**
     * All input files are copied to the [outputDirectory], and any classes are processed.
     */
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputFiles
    var input: ConfigurableFileCollection = project.objects.fileCollection()

    /**
     * Destination directory, where all [input] files will be copied.
     */
    @OutputDirectory
    var outputDirectory: Property<File> = project.objects.property(File::class.java)

    /**
     * A provider that lists all of the files in the [outputDirectory], exploding any .jar files in that directory using
     * [zipTree][org.gradle.api.Project.zipTree].
     */
    @get:Internal
    @Suppress("IMPLICIT_CAST_TO_ANY")
    val outputZipTree: Provider<Iterable<Any>>
        get() = outputDirectory.map { dir ->
            project.fileTree(dir).map { file ->
                if (file.name.endsWith(".jar")) project.zipTree(file) else file
            }
        }

    private val File.inDest get() = File(outputDirectory.get(), name)

    @TaskAction
    fun run(inputs: IncrementalTaskInputs) {
        if (!inputs.isIncremental) {
            outputDirectory.get().deleteRecursively()
        }

        val urls = input.map { it.toURI().toURL() }.toTypedArray()
        logger.debug { "Using classloader with URLs: ${urls.toList()}" }
        inputs.outOfDate { input ->
            val src = input.file
            if (src.isFile) {
                val dest = src.inDest
                if (src.name.endsWith(".class") || src.name.endsWith(".jar") || src.length() > 1048576) {
                    exec.submit(KraalFile::class.java) { config ->
                        config.isolationMode = IsolationMode.NONE
                        config.params(src, dest, urls)
                    }
                } else {
                    src.copyTo(dest, overwrite = true)
                }
            }
        }
        inputs.removed { input ->
            val src = input.file
            val dest = src.inDest
            if (dest.isFile) dest.delete()
        }
    }
}

internal class KraalFile @Inject constructor(
    private val src: File,
    private val dest: File,
    private val classpath: Array<URL>
) : Runnable {

    override fun run() {
        src.copyTo(dest, overwrite = true)
        if (src.name.endsWith(".class") || src.name.endsWith(".jar")) {
            val classloader = URLClassLoader(classpath, Thread.currentThread().contextClassLoader)
            removeIrreducibleLoops(dest.toPath(), classloader)
        }
    }
}
