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

import org.quicktheories.QuickTheory.qt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NodeSplitterTest : LowerCaseLetters {

    private fun TestDigraph.cloneTestNodes(): (Collection<TestNode>, Collection<TestNode>, (TestNode, InsertionLocation<TestNode>) -> Unit) -> Unit =
        { toClone, ownedBy, addVertex ->

            val clones = toClone.map {
                val newNode = TestNode("${it.content}-${vertices.size}", vertices.size)
                vertices += newNode
                newNode
            }

            toClone.forEachIndexed { i, node ->
                val newNode = clones[i]

                for (successor in node.successors) {
                    if (successor !in toClone) {
                        newNode.successors += successor
                        successor.predecessors += newNode
                    } // else handled below as predecessor
                }

                val predIterator = node.predecessors.iterator()
                while (predIterator.hasNext()) {
                    val predecessor = predIterator.next()
                    if (predecessor in ownedBy) {
                        predIterator.remove()
                        newNode.predecessors += predecessor
                        predecessor.successors -= node
                        predecessor.successors += newNode
                    } else if (predecessor in toClone) {
                        val clonedPred = clones[toClone.indexOf(predecessor)]
                        newNode.predecessors += clonedPred
                        clonedPred.successors += newNode
                    }
                }

                addVertex(newNode, Append)
            }
        }

    private fun TestDigraph.isReducible() {
        assertFalse(removeIrreducibleLoops(cloneTestNodes()))
    }

    private fun TestDigraph.isNodeSplitTo(expected: TestGraphBuilder.() -> Unit) {
        removeIrreducibleLoops(cloneTestNodes())
        val expectedVertices = TestGraphBuilder().apply(expected).build().vertices.sortedBy { it.content }
        val actualVertices = vertices.sortedBy { it.content }
        assertEquals(expectedVertices, actualVertices)

        expectedVertices.zip(actualVertices).forEach { (expectedVertex, actualVertex) ->
            assertEquals(expectedVertex.successors, actualVertex.successors, "successors of $expectedVertex")
            assertEquals(expectedVertex.predecessors, actualVertex.predecessors, "predecessors of $expectedVertex")
        }
    }

    @Test
    fun `single node is reducible`() = graph {
        unconnected(a)
    }.isReducible()

    @Test
    fun `simple daisy chain is reducible`() = graph {
        a to b to c
    }.isReducible()

    @Test
    fun `self-cycle is reducible`() = graph {
        a to a
    }.isReducible()

    @Test
    fun `simple cycle is reducible`() = graph {
        a to b to a
    }.isReducible()

    @Test
    fun `simple cycle with extra vertices is reducible`() = graph {
        a to b to a
        a to c to d
        b to d
    }.isReducible()

    @Test
    fun `simple irreducible loop`() = graph {
        a to b to c to b
        a to c
    }.isNodeSplitTo {
        a to b to c to "b-3" to c
        a to c
    }

    @Test
    fun `irreducible loop with extra daisy chains`() = graph {
        a to b to c to b
        a to c to d to e
        a to f
    }.isNodeSplitTo {
        a to b to c to "b-6" to c
        a to c to d to e
        a to f
    }

    @Test
    fun `irreducible loop with one lighter weight node`() = graph {
        a to b to c to d to b
        a to d
    }.isNodeSplitTo {
        a to b to c to "d-4" to b
        a to d to b
    }

    @Test
    fun `irreducible loop requires multiple splits`() = graph {
        a to b to c to b
        a to d to b to d
        a to c
    }.isNodeSplitTo {
        a to b to c to b
        a to d to b to "d-5" to b
        a to "c-4" to b
    }

    @Test
    fun `simple irreducible loop where each node is daisy chain`() = graph {
        a to b to c // these three correspond to node "a" in simple test
        d to e to f // these three correspond to node "b" in simple test
        g to h to i // these three correspond to node "c" in simple test

        c to d // "a" to "b"
        c to g // "a" to "c"
        f to g // "b" to "c"
        i to d // "c" to "b"

    }.isNodeSplitTo {
        a to b to c
        d to e to f
        g to h to i
        "d-9" to "e-10" to "f-11" // clone of "b"

        c to d
        c to g
        f to g
        i to "d-9"
        "f-11" to g
    }

    @Test
    fun `simple irreducible loop where each node is natural loop`() = graph {
        a to b to c to a // these three correspond to node "a" in simple test
        d to e to f to d // these three correspond to node "b" in simple test
        g to h to i to g // these three correspond to node "c" in simple test

        c to d // "a" to "b"
        c to g // "a" to "c"
        f to g // "b" to "c"
        i to d // "c" to "b"

    }.isNodeSplitTo {
        a to b to c to a
        d to e to f to d
        g to h to i to g
        "d-9" to "e-10" to "f-11" to "d-9"// clone of "b"

        c to d
        c to g
        f to g
        i to "d-9"
        "f-11" to g
    }

    // random graph that caught issue with cloneTestNodes()
    @Test
    fun `abnormal irreducible graph`() = graph {
        a to a
        b to a
        c to a
    }.isNodeSplitTo {
        a to a
        b to a
        c to "a-3" to "a-3"
    }

    // random graph that caught issue with region merge resulting in new region with self as single predecessor
    @Test
    fun `abnormal irreducible graph2`() = graph {
        a to a
        b to a
        c to a
        c to d to c
    }.isNodeSplitTo {
        a to a
        b to a
        c to "a-4" to "a-4"
        c to d to c
    }

    @Test
    fun `removeIrreducible loops from random graphs`() {
        qt().forAll(randomGraph()).check { orig ->
            val graph = orig.clone()
            // either there are no irreducible loops
            !graph.removeIrreducibleLoops(graph.cloneTestNodes())
                    // or they are eliminated and not present after the first invocation
                    || !graph.removeIrreducibleLoops(graph.cloneTestNodes())
        }
    }
}