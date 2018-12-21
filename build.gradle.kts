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
import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.11"
    id("io.gitlab.arturbosch.detekt") version "1.0.0-RC11"
}

description = "Kraal parent module"

allprojects {
    group = "com.hpe.kraal"
    version = "0.0.1-SNAPSHOT"

    repositories {
        jcenter()
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "kotlin")

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs += listOf("-progressive")
            // disable -Werror with: ./gradlew -PwarningsAsErrors=false
            allWarningsAsErrors = project.findProperty("warningsAsErrors") != "false"
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events(
                TestLogEvent.SKIPPED,
                TestLogEvent.FAILED
            )
            exceptionFormat = TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }
}

allprojects {
    dependencies.constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:1.3.11")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.11")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-common:1.3.11")
        implementation("org.jetbrains.kotlin:kotlin-reflect:1.3.11")
        implementation("org.jetbrains.kotlin:kotlin-test:1.3.11")
        implementation("org.jetbrains.kotlin:kotlin-test-common:1.3.11")
        implementation("org.jetbrains.kotlin:kotlin-test-junit5:1.3.11")

        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.0.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.0.1")

        implementation("org.ow2.asm:asm:7.0")
        implementation("org.ow2.asm:asm-analysis:7.0")
        implementation("org.ow2.asm:asm-tree:7.0")
        implementation("org.ow2.asm:asm-util:7.0")

        implementation("org.slf4j:slf4j-api:1.7.25")
        implementation("org.slf4j:slf4j-simple:1.7.25")

        implementation("io.ktor:ktor-server-core:1.0.1")
        implementation("io.ktor:ktor-jackson:1.0.1")
        implementation("io.ktor:ktor-server-netty:1.0.1")
        implementation("io.ktor:ktor-server-jetty:1.0.1")
        implementation("io.ktor:ktor-server-cio:1.0.1")
        implementation("io.ktor:ktor-client-core-jvm:1.0.1")
        implementation("io.ktor:ktor-client-apache:1.0.1")
        implementation("io.ktor:ktor-server-test-host:1.0.1")

        implementation("io.gitlab.arturbosch.detekt:detekt-core:1.0.0-RC11")
        implementation("io.gitlab.arturbosch.detekt:detekt-api:1.0.0-RC11")
        implementation("io.gitlab.arturbosch.detekt:detekt-test:1.0.0-RC11")
        implementation("io.gitlab.arturbosch.detekt:detekt-formatting:1.0.0-RC11")

        implementation("org.quicktheories:quicktheories:0.25")
	}

    dependencies {
        // Maven BOMs create constraints when depended on *outside the constraints section*
        implementation(enforcedPlatform("org.junit:junit-bom:5.3.2"))
    }

    configurations.all {
        // We use junit5, not junit4
        exclude("junit", "junit")
    }
}

// run Detekt on all subprojects
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    dependencies {
        detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.0.0-RC11")
    }

    tasks.named<Detekt>("detekt") {
        config = files("$rootDir/detekt.yml")
    }
}

subprojects {
    // dependencies for all projects
    dependencies {
        implementation(kotlin("stdlib-jdk8"))
        testImplementation(kotlin("test-junit5"))
        testImplementation("org.junit.jupiter:junit-jupiter-engine:5.3.2")
    }
}