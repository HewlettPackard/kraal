# Kraal Example #

An example of using Kraal with Maven to build a native image of a simple Ktor webapp.

## Building ##

If using the unreleased version of Kraal (i.e. building master), first build Kraal from the parent directory:

    ./gradlew publishToMavenLocal
    cd maven-example

Then, with GraalVM installed locally:

    mvn install
    native-image \
        --static \
        --report-unsupported-elements-at-runtime \
        -jar target/maven-example.jar
    ./maven-example

Or, using a Docker container with GraalVM and building the example webapp into a 26MB Docker image:

    mvn install
    docker build -f Dockerfile -t kraal-example target
    docker run --rm -P kraal-example
