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
description = "Gradle plugin for Kraal: Kraal enables the use of Kotlin coroutines and GraalVM native-image together"

plugins {
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "0.10.0"
}

dependencies {
    api(gradleApi())
    implementation(project(":kraal"))
}

pluginBundle {
    website = "https://github.com/HewlettPackard/kraal"
    vcsUrl = "https://github.com/HewlettPackard/kraal"
    tags = listOf("kotlin", "coroutines", "graal", "graalvm", "graal-native", "native-image")
}
gradlePlugin {
    plugins {
        create("kraalPlugin") {
            id = "com.hpe.kraal"
            implementationClass = "com.hpe.kraal.gradle.KraalPlugin"
            displayName = "Kraal Plugin"
            description = "Enables the use of Kotlin coroutines and GraalVM native-image together"
        }
    }
}