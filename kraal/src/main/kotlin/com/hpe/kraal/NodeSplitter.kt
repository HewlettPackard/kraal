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

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.hpe.kraal.NodeSplitterKt")

/**
 * Remove irreducible loops from the receiving graph.
 *
 * The algorithm iteratively groups connected vertices into regions, and clones regions as needed to eliminate
 * irreducible loops.
 *
 * Take a simple example with an irreducible loop:
 * ```
 * A -> B -> C
 * A -> C
 * C -> B
 * ```
 *
 * The algorithm will choose to clone either region B or region C, based on the number of vertices in each region.
 * Suppose for the sake of example we choose to clone region B.
 *
 * We will arbitrarily select one predecessor of B to continue to be its predecessor, and create a clone of B for each
 * of its *other* predecessors.  Again, for the sake of example, suppose we choose to keep A as the predecessor of the
 * original copy of B.
 *
 * After cloning, the updated graph will be:
 * ```
 * A -> B -> C
 * A -> C
 * C -> B' -> C
 * ```
 *
 * To allow this algorithm to function on arbitrary graphs, the work of actually cloning the vertices in a region is
 * delegated to the given [cloneVertices] callback.  To allow the cloning logic to add vertices to regions other than
 * the region being cloned, the cloneVertices callback is passed an `addVertex` callback, which must be used for each
 * vertex added by cloneVertices.
 *
 * The cloneVertices callback must leave the underlying graph in precisely the correct state:
 * 1. Every edge from a vertex in `ownedBy` to a vertex in `toClone` is updated to be between the vertex in `ownedBy`
 * and the copy of the vertex from `toClone`.
 * 1. Every edge from a vertex in `toClone` is copied to also exist between the copy of the vertex and the same target.
 * 1. Other edges are not modified.
 *
 * The behavior of the cloneVertices callback is partially validated by this function.  Namely, edges between regions of
 * vertices must match the expected result.
 */
internal fun <V : Vertex<V>> Digraph<V>.removeIrreducibleLoops(
    cloneVertices: (
        toClone: Collection<V>,
        ownedBy: Collection<V>,
        addVertex: (vertex: V, location: InsertionLocation<V>) -> Unit
    ) -> Unit
): Boolean {

    // This is a well-known simple algorithm.  The first publication I'm certain of is:
    // Compilers: Principles, Techniques, and Tools. Compilers: Principles, Techniques, and Tools in 1986
    // http://www.informatik.uni-bremen.de/agbkb/lehre/ccfl/Material/ALSUdragonbook.pdf (section 9.7.6)
    // Though I'm fairly sure it has been around longer than that.

    // This approach is not optimal - it may clone more vertices than strictly needed to make the graph reducible.
    // An optimized algorithm was published in 2001:
    // Handling Irreducible Loops: Optimized Node Splitting vs. DJ-Graphs
    // http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.470.3729&rep=rep1&type=pdf

    // We do not attempt to implement the optimized algorithm, which is considerably more complex, but we do use their
    // (T1, T2, T3) terminology from the "Traditional Node Splitting" section.

    // start with a region for every vertex
    val regions = createInitialRegions()

    var anyRegionsCloned = false

    // The algorithm terminates when we're left with a single region.
    // Or, if the control flow graph is somehow not connected, then we will have multiple regions without predecessors.
    while (regions.size > 1 && regions.any { it.predecessors.isNotEmpty() }) {

        // T1: Remove any edge that connects a node to itself
        var t1OrT2Matched = t1(regions)

        // T2: If any node has exactly one predecessor, replace this node and its predecessor with a single new node
        t1OrT2Matched = t2(regions) || t1OrT2Matched

        // T3: Applied if neither T1 nor T2 are applicable anymore.  Choose any node with at least two predecessors.
        // Duplicate this node so that there is one copy for each of its predecessors.
        if (!t1OrT2Matched) {
            t3(regions, cloneVertices)
            anyRegionsCloned = true
        }
    }

    if (anyRegionsCloned) {
        log.info { "Removed non-reducible loops in $name" }
    }

    return anyRegionsCloned
}

/**
 * Remove any edge that connects a node to itself
 */
private fun <V : Vertex<V>> t1(regions: MutableList<Region<V>>): Boolean {
    var anyMatched = false
    for (region in regions) {
        if (region.predecessors.remove(region)) {
            region.successors.remove(region)
            anyMatched = true
        }
    }
    return anyMatched
}

/**
 * If any node has exactly one predecessor, replace this node and its predecessor with a single new node
 */
private fun <V : Vertex<V>> t2(regions: MutableList<Region<V>>): Boolean {
    var anyMatched = false
    val regionIterator = regions.iterator()
    while (regionIterator.hasNext()) {
        val region = regionIterator.next()
        // exclude regions that are their own predecessors, which were created by merging a previous region
        if (region.predecessors.size == 1 && region.predecessors.single() !== region) {
            // Merge the region into its single predecessor.
            val pred = region.predecessors.single()
            pred.vertices += region.vertices
            pred.successors -= region
            pred.successors += region.successors
            region.successors.forEach { s ->
                s.predecessors -= region
                s.predecessors += pred
            }
            regionIterator.remove()
            anyMatched = true
        }
    }
    return anyMatched
}

/**
 * Choose any node with at least two predecessors.  Duplicate this node so that there is one copy for each of its
 * predecessors.
 */
private fun <V : Vertex<V>> Digraph<V>.t3(
    regions: MutableList<Region<V>>,
    cloneVertices: (
        toClone: Collection<V>,
        ownedBy: Collection<V>,
        addVertex: (vertex: V, location: InsertionLocation<V>) -> Unit
    ) -> Unit
) {
    if (log.isDebugEnabled) {
        printAsDOT { isInteresting(it) }
        regions.asGraph(name).printAsDOT()
    }

    val regionToClone = regions.filter { it.predecessors.size > 1 }.minBy { it.weight }!!
    log.info {
        "Found region to clone $regionToClone in $name with size ${regionToClone.vertices.size} and " +
                "${regionToClone.predecessors.size} predecessor regions"
    }

    // The first predecessor region will continue to own the original region we're cloning.
    // Make clones of the region for all of its other predecessors.
    regionToClone.predecessors.drop(1).forEach { predRegion ->
        log.debug { "Cloning $regionToClone for $predRegion" }
        val clonedRegion = Region(
            index = regions.size,
            vertices = mutableListOf(),
            successors = regionToClone.successors.toMutableSet(),
            predecessors = mutableSetOf(predRegion)
        )

        regions += clonedRegion
        regionToClone.predecessors -= predRegion
        predRegion.successors -= regionToClone
        predRegion.successors += clonedRegion
        regionToClone.successors.forEach { it.predecessors += clonedRegion }

        cloneVertices(regionToClone.vertices, predRegion.vertices) { vertex, location ->
            // Callback for when a vertex is added - add it to the appropriate region.
            // Note that we can't just have cloneVertices return the collection of new vertices
            // because the clone logic needs to be able to add new vertices to *other* regions.
            val region = when (location) {
                is Append -> clonedRegion
                is RelativeLocation -> regions.first { location.relativeTo in it.vertices }
            }
            region.vertices.add(region.vertices.indexOfLast { it.index < vertex.index } + 1, vertex)
        }

        log.info { "Added $clonedRegion as clone of $regionToClone for $predRegion" }
    }

    if (log.isDebugEnabled) {
        printAsDOT { isInteresting(it) }
        regions.asGraph(name).printAsDOT()
    }

    verifyRegionEdgesMatchUnderlyingEdges(regions)
}

/**
 * A region is a group of connected vertices that have been grouped together during the graph reduction algorithm.
 */
private class Region<V : Vertex<V>>(
    override var index: Int,
    val vertices: MutableList<V>,
    val successors: MutableSet<Region<V>> = mutableSetOf(),
    val predecessors: MutableSet<Region<V>> = mutableSetOf()
) : Vertex<Region<V>> {

    override fun toString(): String {
        val indexRanges = mutableListOf<IntRange>()
        vertices.sortedBy { it.index }.forEach { node ->
            val existingRange = indexRanges.indexOfFirst { it.endInclusive + 1 == node.index }
            if (existingRange != -1) {
                indexRanges[existingRange] = indexRanges[existingRange].start..node.index
            } else {
                indexRanges += node.index..node.index
            }
        }
        return "r$index~" + indexRanges.joinToString(";") { r ->
            if (r.start == r.endInclusive) r.start.toString() else r.toString()
        }
    }
}

private fun <V : Vertex<V>> Digraph<V>.createInitialRegions(): MutableList<Region<V>> {
    // TODO: for performance, we could avoid creating trivial "daisy chain" regions and then immediately combining them
    val regions = vertices.map { Region(it.index, mutableListOf(it)) }.toMutableList()
    vertices.forEachIndexed { index, vertex ->
        regions[index].successors += successorsOf(vertex).map { regions[it.index] }
        regions[index].predecessors += predecessorsOf(vertex).map { regions[it.index] }
    }
    return regions
}

/**
 * The weight of a region is an estimate of the number of vertices that would be created by cloning a region.  We prefer
 * to clone regions with smaller weights.
 */
private val Region<*>.weight: Int
    get() = (predecessors.size - 1) * vertices.size

/**
 * We just keep a list of regions, because we have no need for functions that require a Digraph object - except for
 * debugging, then we create a temporary digraph object.
 */
private fun <V : Vertex<V>> Collection<Region<V>>.asGraph(name: String) = object : Digraph<Region<V>> {
    override val vertices = this@asGraph
    override fun successorsOf(vertex: Region<V>) = vertex.successors
    override fun predecessorsOf(vertex: Region<V>) = vertex.predecessors
    override val name = "$name regions"
}

/**
 * Verify that the `cloneVertices` callback left the graph in the expected state.
 */
private fun <V : Vertex<V>> Digraph<V>.verifyRegionEdgesMatchUnderlyingEdges(regions: List<Region<V>>) {
    val errors = mutableListOf<String>()
    for (region in regions) {
        val actualSuccessors = region.vertices.flatMap { successorsOf(it) }.toSet().map { v ->
            regions.first { v in it.vertices }
        }.toSet() - region

        if (actualSuccessors != region.successors - region) {
            val unexpectedSuccessors = region.vertices.flatMap { successorsOf(it) }.distinct().filter { s ->
                s !in region.vertices && region.successors.none { s in it.vertices }
            }
            val membersWithUnexpectedSuccessors = region.vertices.filter { v ->
                successorsOf(v).any { it in unexpectedSuccessors }
            }

            errors += "Region $region expected successors ${(region.successors - region).sortedBy { it.index }}, " +
                    "but was ${(actualSuccessors).sortedBy { it.index }}.  " +
                    "$membersWithUnexpectedSuccessors unexpectedly have successors $unexpectedSuccessors"
        }

        val actualPredecessors = region.vertices.flatMap { predecessorsOf(it) }.toSet().map { v ->
            regions.first { v in it.vertices }
        }.toSet() - region

        if (actualPredecessors != region.predecessors - region) {
            errors += "Region $region expected predecessors ${(region.predecessors - region).sortedBy { it.index }}, " +
                    "but was ${(actualPredecessors).sortedBy { it.index }}"
        }
    }

    check(errors.isEmpty()) {
        "Error processing $name: ${errors.joinToString("\n")}"
    }
}
