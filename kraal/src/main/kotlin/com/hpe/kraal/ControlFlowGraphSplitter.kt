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

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.hpe.kraal.ControlFlowGraphSplitterKt")

/**
 * Process the given ASM [method] to remove irreducible loops and return true if any were found.
 */
internal fun removeIrreducibleLoops(classNode: ClassNode, method: MethodNode): Boolean {
    val modified = removeIrreducibleLoopsWithoutVerification(classNode, method)
    if (modified) {
        verifyProcessedCorrectly(method, classNode)
    }
    return modified
}

private fun removeIrreducibleLoopsWithoutVerification(classNode: ClassNode, method: MethodNode): Boolean {
    // here, we simply glue together the generic digraph NodeSplitter
    // and the bytecode instruction-specific InstructionCloner
    if (method.instructions.size() == 0) return false
    log.debug { "Computing control flow graph for ${classNode.name}.${method.name}" }
    val graph = method.computeControlFlowGraph(classNode)
    if (graph.guaranteedAcyclic) return false

    return graph.removeIrreducibleLoops { toClone, ownedBy, addInstruction ->
        graph.cloneRegion(toClone, ownedBy, addInstruction)
    }
}

private fun MethodControlFlowGraph.cloneRegion(
    toClone: Collection<Instruction>,
    ownedBy: Collection<Instruction>,
    addToRegion: (vertex: Instruction, location: InsertionLocation<Instruction>) -> Unit
) {
    cloneInstructions(toClone, ownedBy) { toAdd, location ->
        val index = when (location) {
            is Before -> {
                method.instructions.insertBefore(location.next.asm, toAdd)
                location.next.index
            }
            is After -> {
                method.instructions.insert(location.prev.asm, toAdd)
                location.prev.index + 1
            }
            is Append -> {
                method.instructions.add(toAdd)
                vertices.size
            }
        }

        val instruction = Instruction(toAdd, method.instructions)
        instructions.add(index, instruction)
        addToRegion(instruction, location)
        instruction
    }
}

private fun verifyProcessedCorrectly(method: MethodNode, classNode: ClassNode) {
    val newCFG = method.computeControlFlowGraph(classNode)
    check(!removeIrreducibleLoopsWithoutVerification(classNode, method)) {
        "After removeIrreducibleLoops, still found loop(s) with undominated entrypoints in ${newCFG.name}"
    }
}
