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
package com.hpe.kraal

import org.quicktheories.core.Gen
import org.quicktheories.generators.SourceDSL.integers
import java.util.*
import java.util.function.BiFunction

class TestNode(
    val content: String,
    override val index: Int,
    val successors: MutableSet<TestNode> = mutableSetOf(),
    val predecessors: MutableSet<TestNode> = mutableSetOf()
) : Vertex<TestNode> {

    override fun toString() = content

    override fun hashCode() = Objects.hash(content)
    override fun equals(other: Any?) = other is TestNode && content == other.content
}

data class TestDigraph(
    override val vertices: MutableList<TestNode>,
    override val root: TestNode = vertices.first()
) : RootedDigraph<TestNode> {
    override val name = "TestDigraph"
    override fun successorsOf(vertex: TestNode): Set<TestNode> = vertex.successors
    override fun predecessorsOf(vertex: TestNode): Set<TestNode> = vertex.predecessors

    operator fun get(char: Char): TestNode = vertices.first { it.content == "$char" }

    override fun toString(): String {
        val sb = StringBuilder()
        for (node in vertices) {
            val successors = successorsOf(node)
            for (successor in successors) {
                sb.append("$node to $successor\n")
            }
            if (successors.isEmpty() && predecessorsOf(node).isEmpty()) {
                sb.append("$node\n")
            }
        }
        return sb.toString()
    }

    fun clone(): TestDigraph {
        val clone = TestDigraph(vertices.map { TestNode(it.content, it.index) }.toMutableList())
        clone.vertices.forEachIndexed { i, v ->
            v.successors += vertices[i].successors.map { clone.vertices[it.index] }
            v.predecessors += vertices[i].predecessors.map { clone.vertices[it.index] }
        }
        return clone
    }
}

class TestGraphBuilder(private val edges: MutableList<Pair<String, String>> = mutableListOf(), private val unconnected: MutableList<String> = mutableListOf()) :
    LowerCaseLetters {

    fun build(): TestDigraph {
        val nodes = (edges.flatMapTo(mutableSetOf()) { listOf(it.first, it.second) } + unconnected)
            .sorted()
            .mapIndexed { i, c -> TestNode(c.toString(), i) }
            .toMutableList()

        val contentToNode = nodes.associate { it.content to it }

        for (edge in edges) {
            val first = contentToNode.getValue(edge.first)
            val second = contentToNode.getValue(edge.second)
            first.successors += second
            second.predecessors += first
        }

        return TestDigraph(nodes, nodes.first())
    }

    infix fun String.to(dest: String): String {
        edges += Pair(this.toString(), dest.toString())
        return dest // for chaining
    }

    fun unconnected(vararg vertices: String) {
        unconnected.addAll(vertices.map { it.toString() })
    }
}

fun graph(builder: TestGraphBuilder.() -> Unit): TestDigraph = TestGraphBuilder().apply(builder).build()

interface LowerCaseLetters {
    val a get() = "a"
    val b get() = "b"
    val c get() = "c"
    val d get() = "d"
    val e get() = "e"
    val f get() = "f"
    val g get() = "g"
    val h get() = "h"
    val i get() = "i"
    val j get() = "j"
    val k get() = "k"
    val l get() = "l"
    val m get() = "m"
    val n get() = "n"
    val o get() = "o"
    val p get() = "p"
    val q get() = "q"
    val r get() = "r"
    val s get() = "s"
    val t get() = "t"
    val u get() = "u"
    val v get() = "v"
    val w get() = "w"
    val x get() = "x"
    val y get() = "y"
    val z get() = "z"
}

fun Int.toLowerCaseLetter(): String = ('a'.toInt() + this).toChar().toString()

/**
 * Create a new `Gen` that calls the receiving `Gen` multiple [times] and returns a list of the results.
 */
private fun <R> Gen<R>.repeat(times: Int): Gen<List<R>> = Gen { rand ->
    (0..times).map { generate(rand) }
}

fun randomGraph(minVertices: Int = 1, maxVertices: Int = 20, minEdges: Int = 0, maxEdges: Int = 40): Gen<TestDigraph> {

    val size = integers().between(minVertices, maxVertices)
        .zip(integers().between(minEdges, maxEdges),
            BiFunction<Int, Int, Pair<Int, Int>> { numV, numE -> Pair(numV, numE) })

    val graphGen = size.flatMap { (numV, numE) ->
        val edgeGen = integers()
            .between(0, numV - 1).mix(integers().between(0, numV - 1)).map { first, second ->
                Pair(first.toLowerCaseLetter(), second.toLowerCaseLetter())
            }
        edgeGen.repeat(numE).map { edges ->
            // pass all vertices as "unconnected" for inclusion in the graph, rather than finding which actually are
            TestGraphBuilder(edges.toMutableList(), (0 until numV).map { it.toLowerCaseLetter() }.toMutableList()).build()
        }
    }

    return graphGen
}