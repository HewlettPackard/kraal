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
description = "Kraal enables the use of Kotlin coroutines and GraalVM native-image together"

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.ow2.asm:asm")
    implementation("org.ow2.asm:asm-analysis")
    implementation("org.ow2.asm:asm-tree")
    implementation("org.ow2.asm:asm-util")

    testImplementation("org.slf4j:slf4j-simple")
    testImplementation("org.quicktheories:quicktheories")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
    testImplementation("io.ktor:ktor-server-cio")
    testImplementation("io.ktor:ktor-server-jetty")
    testImplementation("io.ktor:ktor-network")
    testImplementation("io.ktor:ktor-utils")
}