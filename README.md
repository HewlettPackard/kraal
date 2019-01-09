# Kraal #

[ ![Download](https://api.bintray.com/packages/bradnewman/kraal/kraal/images/download.svg) ](https://bintray.com/bradnewman/kraal/kraal/_latestVersion)
[![Build Status](https://travis-ci.org/HewlettPackard/kraal.svg?branch=master)](https://travis-ci.org/HewlettPackard/kraal)

Attempting to use [GraalVM](https://www.graalvm.org/) and [Kotlin](http://kotlinlang.org/)
[coroutines](http://kotlinlang.org/docs/reference/coroutines-overview.html) together fails due to a
[limitation of GraalVM](https://github.com/oracle/graal/issues/366) - it cannot handle some perfectly valid
bytecode produced by the Kotlin compiler.

Kraal performs "node splitting" on Java bytecode in order to eliminate the irreducible loops produced by the
Kotlin compiler that GraalVM doesn't handle.  The result is a functionally-equivalent bytecode which can be
successfully processed with GraalVM.

Eventually, GraalVM may support irreducible loops, or the Kotlin compiler may add a flag to produce bytecode without them.
Until then, Kraal allows the usage of these two technologies together.

 * https://github.com/oracle/graal/issues/366

## Usage ##

### Gradle ###

Using the Kotlin DSL:

    plugins {
        id("com.hpe.kraal") version "0.0.11"
    }

The Kraal Gradle plugin by default takes all jars produced by the build and their runtime dependencies,
processes them to remove irreducible loops, and leaves processed copies of the jars under `build/kraal`.

The `kraal` extension is provided for convenient configuration of the default execution of `KraalTask`:

    kraal {
        input = files(...) // *replace* set of input files
        from(...)          // or, *add* to set of input files

        outputDirectory = file(...)
    }

The task and extension expose properties that create a `Provider` of a `zipTree` of the processed versions of all of the files.
This can be used as a convenient way to create a "fat jar" of the processed classes:

    val fatjar by tasks.creating(Jar::class) {
        from(kraal.outputZipTrees) {
            exclude("META-INF/*.SF")
            exclude("META-INF/*.DSA")
            exclude("META-INF/*.RSA")
        }
    }

See the [example](https://github.com/HewlettPackard/kraal/tree/master/example) directory for a complete working example with [Ktor](https://ktor.io/) and GraalVM.

## Maven ##

Use the `exec-maven-plugin` to execute the Kraal driver application `com.hpe.kraal.MainKt`:

    <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>1.6.0</version>
        <dependencies>
            <dependency>
                <groupId>com.hpe.kraal</groupId>
                <artifactId>kraal</artifactId>
                <version>0.0.11</version>
            </dependency>
        </dependencies>
        <executions>
            <execution>
                <goals>
                    <goal>java</goal>
                </goals>
                <phase>package</phase>
                <configuration>
                    <mainClass>com.hpe.kraal.MainKt</mainClass>
                    <arguments>
                        <argument>${project.build.directory}/${project.build.finalName}.${project.packaging}</argument>
                    </arguments>
                    <includePluginDependencies>true</includePluginDependencies>
                </configuration>
            </execution>
        </executions>
    </plugin>

See the [maven-example](https://github.com/HewlettPackard/kraal/tree/master/maven-example) directory for a complete working example with [Ktor](https://ktor.io/) and GraalVM.
