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
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.21"
    id("com.hpe.kraal") version "0.0.15" // kraal version - for makeRelease.sh
}

repositories {
    jcenter()
}

description = "Kraal example with Ktor"

group = "com.hpe.kraal"
version = "0.0.15" // kraal version - for makeRelease.sh

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf("-progressive")
        // disable -Werror with: ./gradlew -PwarningsAsErrors=false
        allWarningsAsErrors = project.findProperty("warningsAsErrors") != "false"
    }
}

dependencies {
    implementation("org.slf4j:slf4j-simple:1.7.25")
    implementation("io.ktor:ktor-server-jetty:1.1.2")
}

// create a "fat" jar with application and all dependencies processed by Kraal
val fatjar by tasks.creating(Jar::class) {

    from(kraal.outputZipTrees) {
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }

    manifest {
        attributes("Main-Class" to "com.hpe.kraal.example.AppKt")
    }

    destinationDir = project.buildDir.resolve("fatjar")
    archiveName = "example.jar"
}

tasks.named("assemble").configure {
    dependsOn(fatjar)
}
