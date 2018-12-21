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
package com.hpe.kraal.gradle

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Provider
import java.io.File

open class KraalExtension(private val project: Project) {

    /**
     * Input .jar and .class files to process.  Defaults to the runtimeClasspath and the output of the "jar" task.
     */
    var input: ConfigurableFileCollection = project.layout.configurableFiles()
        .from(project.configurations.named("runtimeClasspath"))
        .from(project.tasks.named("jar"))

    /**
     * Output directory, will contain all input files with only the ones containing irreducible loops modified.
     * Defaults to a "kraal" subdirectory under the project build dir.
     */
    var outputDirectory: File = project.buildDir.resolve("kraal")

    /**
     * Adds additional [input]s.  The given [files] may be any type accepted by [Project.files].
     */
    fun from(vararg files: Any) {
        input.from(files)
    }

    /**
     * Calls [KraalTask.outputZipTree] for all KraalTasks in the project.
     */
    val outputZipTrees: Iterable<Provider<Iterable<Any>>>
        get() = project.tasks.withType(KraalTask::class.java).map { it.outputZipTree }
}
