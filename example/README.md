# Kraal Example #

An example of using Kraal to build a native image of a simple Ktor webapp.

## Building ##

With GraalVM installed locally:

    ./gradlew build
    native-image \
        --static \
        --report-unsupported-elements-at-runtime \
        -jar build/fatjar/example.jar
    ./example

Or, using a Docker container with GraalVM and building the example webapp into a 26MB Docker image:

    ./gradlew build
    docker build -f Dockerfile -t kraal-example build/fatjar
    docker run --rm -P kraal-example