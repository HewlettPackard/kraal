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

internal interface Vertex<V : Vertex<V>> {
    val index: Int
}

internal interface Digraph<V : Vertex<V>> {
    val name: String
    val vertices: Collection<V>
    fun successorsOf(vertex: V): Set<V>
    fun predecessorsOf(vertex: V): Set<V>
}

internal interface RootedDigraph<V : Vertex<V>> : Digraph<V> {
    val root: V
}

internal sealed class InsertionLocation<out V>
internal object Append : InsertionLocation<Nothing>()
internal sealed class RelativeLocation<out V>(val relativeTo: V) : InsertionLocation<V>()
internal data class Before<out V>(val next: V) : RelativeLocation<V>(next)
internal data class After<out V>(val prev: V) : RelativeLocation<V>(prev)

internal fun <V : Vertex<V>> Digraph<V>.isInteresting(node: V): Boolean =
    predecessorsOf(node).size != 1 || predecessorsOf(node).size != 1

private fun <V : Vertex<V>> Digraph<V>.filteredSuccessors(node: V, filter: Digraph<V>.(V) -> Boolean): Collection<V> =
    successorsOf(node).flatMap { if (filter(it)) listOf(it) else filteredSuccessors(it, filter) }

internal fun <V : Vertex<V>> Digraph<V>.printAsDOT(filter: Digraph<V>.(V) -> Boolean = { true }) {
    // http://www.webgraphviz.com/
    println("digraph \"$name\" {")
    for (f in vertices) {
        if (filter(f)) {
            for (s in filteredSuccessors(f, filter)) {
                println("\"$f\" -> \"$s\"")
            }
        }
    }
    println("}")
}
