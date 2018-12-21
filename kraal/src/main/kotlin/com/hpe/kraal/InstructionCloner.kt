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

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.slf4j.LoggerFactory

/**
 * Makes a copy of a group of instructions [toClone], while preserving functional equivalence.  Edges from the
 * instructions in [ownedBy] to the instructions to clone are updated to instead flow into the equivalent copies of the
 * instructions.  Predecessors of the instructions to clone outside of [ownedBy] are left as-is.
 *
 * The [addInstruction] callback is used to add instructions to the method - the instruction list is not modified
 * directly by this function.  (This is so that the new instructions, which may not all be copies of the instructions
 * to clone, can be added to the proper regions.)
 */
internal fun MethodControlFlowGraph.cloneInstructions(
    toClone: Collection<Instruction>,
    ownedBy: Collection<Instruction>,
    addInstruction: (AbstractInsnNode, InsertionLocation<Instruction>) -> Instruction
) {
    InstructionCloner(this, toClone, ownedBy, addInstruction).cloneInstructions()

    // TODO: would be nice to update edges as we go along, rather than running ASM control flow analysis repeatedly
    resetEdges()
}

@Suppress("LargeClass", "NestedBlockDepth", "TooManyFunctions")
private class InstructionCloner(
    val graph: MethodControlFlowGraph,
    val toClone: Collection<Instruction>,
    val ownedBy: Collection<Instruction>,
    val addInstruction: (AbstractInsnNode, InsertionLocation<Instruction>) -> Instruction
) {
    // 1. Create clones of the labels, but don't add them to the method.
    /**
     * A map of all original labels -> clone of the label, or original label if label was not cloned
     */
    private val clonedLabels = graph.method.instructions.toArray().filterIsInstance<LabelNode>().associateWith { it } +
            toClone.map { it.asm }.filterIsInstance<LabelNode>().associateWith { LabelNode() }

    // 2. Use the clonedLabels and ASM's AbstractInsnNode.clone() to create the clones and append them to the method.
    /**
     * A map of toClone -> copy of each instruction
     */
    private val clones = toClone.associateWith { instruction ->
        addInstruction(instruction.asm.clone(clonedLabels), Append)
    }

    fun cloneInstructions() {
        // 3. Update the edges from the region that this clone was made for (the "owner"),
        // so instead of pointing at the original instructions, they go to the new instructions we created.
        // (Edges from other regions besides the "owner" are left pointing at the original instructions.)
        updateEntrypoints()

        // 4. If the group of instructions that we cloned are in an awkward order,
        // then edges resulting from the sequential execution of the original instructions
        // may need to be added in the cloned instructions by inserting new GOTOs.
        fixSequentialPredecessors()

        // 5. If there are successors to what we cloned from the sequential execution of the original instructions,
        // then we need to add new GOTOs so that the cloned instructions have the same successors as the originals.
        fixSequentialSuccessors()

        // 6. Another type of entrypoint we need to handle comes from try-catches.  If we cloned a "catch",
        // then we need to update or split the corresponding "try" so that instructions in the "owner" region
        // are handled by the clone of the "catch" while other instructions are handled by the original "catch".
        splitTryCatches()

        // 7. If any of the original instructions are covered by "try"s, then create copies of the try-catch entries
        // so that exceptions raised by the cloned instructions are also handled by the corresponding "catch".
        cloneTryCatches()

        verify()

        // TODO: merge adjacent / remove redundant try catches?
    }

    private val tryCatchBlocks get() = graph.method.tryCatchBlocks

    private val AbstractInsnNode.index: Int
        get() = graph.method.instructions.indexOf(this)

    private val AbstractInsnNode.instruction: Instruction
        get() = graph.instructions[index]

    private fun findOrCreateLabel(
        location: InsertionLocation<Instruction>,
        createdCallback: (LabelNode) -> Unit = {}
    ): LabelNode {
        val candidates = when (location) {
            is Before -> listOf(location.next.asm, graph.instructions.getOrNull(location.next.index - 1)?.asm)
            is After -> listOf(location.prev.asm, graph.instructions.getOrNull(location.prev.index + 1)?.asm)
            is Append -> listOf(graph.instructions.lastOrNull())
        }

        return candidates.filterIsInstance<LabelNode>().firstOrNull()
            ?: LabelNode().also { newLabel ->
                addInstruction(newLabel, location)
                createdCallback(newLabel)
            }
    }

    /**
     * If the receiving instruction is a jump and the given [ifExistingTarget] matches, then update the jump to point
     * to the given [target] instead.  Returns true if the instruction was updated.
     */
    private fun AbstractInsnNode.updateJump(target: LabelNode, ifExistingTarget: AbstractInsnNode): Boolean {
        when {
            this is JumpInsnNode && label === ifExistingTarget -> label = target
            this is TableSwitchInsnNode && dflt === ifExistingTarget -> dflt = target
            this is LookupSwitchInsnNode && dflt === ifExistingTarget -> dflt = target
            this is TableSwitchInsnNode && ifExistingTarget in labels -> {
                val index = labels.indexOf(ifExistingTarget)
                labels[index] = target
            }
            this is LookupSwitchInsnNode && ifExistingTarget in labels -> {
                val index = labels.indexOf(ifExistingTarget)
                labels[index] = target
            }
            else -> return false
        }
        return true
    }

    private fun goto(target: LabelNode) = JumpInsnNode(Opcodes.GOTO, target)

    /**
     * Update the edges from the region that this clone was made for (the "owner"),
     * so instead of pointing at the original instructions, they go to the new instructions we created.
     * (Edges from other regions besides the "owner" are left pointing at the original instructions.)
     */
    private fun updateEntrypoints() {
        for (instruction in toClone) {
            for (predecessor in instruction.predecessors) {
                if (predecessor in ownedBy) {
                    val targetLabel = findOrCreateLabel(Before(clones.getValue(instruction)))
                    val predInstr = predecessor.asm
                    // are we updating an existing jump or adding a new GOTO?
                    if (predInstr.updateJump(targetLabel, ifExistingTarget = instruction.asm)) {
                        log.debug { "Modified jump so $predecessor -> clone of $instruction" }
                    } else {
                        val newGoto = addInstruction(goto(targetLabel), After(predecessor))
                        log.debug { "Inserted $newGoto so $predecessor -> clone of $instruction" }
                    }
                }
            }
        }
    }

    private infix fun Instruction.isSequentialBefore(next: Instruction): Boolean = index + 1 == next.index

    private fun origSequentialButClonesNot(first: Instruction, second: Instruction): Boolean {
        val cloneOfFirst = clones.getValue(first)
        val cloneOfSecond = clones.getValue(second)
        return first isSequentialBefore second && !(cloneOfFirst isSequentialBefore cloneOfSecond)
    }

    /**
     * If the group of instructions that we cloned are in an awkward order, then edges resulting from the sequential
     * execution of the original instructions may need to be added in the cloned instructions by inserting new GOTOs.
     */
    private fun fixSequentialPredecessors() {
        for (instruction in toClone) {
            for (predecessor in instruction.predecessors) {
                if (predecessor in toClone && origSequentialButClonesNot(predecessor, instruction)) {
                    val targetLabel = findOrCreateLabel(Before(clones.getValue(instruction)))
                    val newGoto = addInstruction(goto(targetLabel), After(clones.getValue(predecessor)))
                    log.debug { "Inserted $newGoto so clone of $predecessor -> clone of $instruction" }
                }
            }
        }
    }

    /**
     * If there are successors to what we cloned from the sequential execution of the original instructions,
     * then we need to add new GOTOs so that the cloned instructions have the same successors as the originals.
     */
    private fun fixSequentialSuccessors() {
        for (instruction in toClone) {
            for (successor in instruction.successors) {
                // If the successor is in toClone, the edge was handled when succ was cloned.
                // If the successor is a jump outside of toClone, then the target will remain the same after cloning.
                // But if the successor is outside of toClone and the edge exists by virtue of sequential execution,
                // then we need to add a *new* jump from the cloned instruction to this successor.
                if (successor !in toClone && instruction isSequentialBefore successor) {
                    val targetLabel = findOrCreateLabel(Before(successor))
                    val newGoto = addInstruction(goto(targetLabel), After(clones.getValue(instruction)))
                    log.debug { "Inserted $newGoto so clone of $instruction -> $successor" }
                }
            }
        }
    }

    private fun Iterable<Instruction>.toIndexRanges(): List<IntRange> {
        return map { it.index..it.index }.fold(mutableListOf()) { result, range ->
            val last = result.lastOrNull()
            if (last?.endInclusive == range.start - 1) {
                result[result.size - 1] = last.start..range.endInclusive
            } else {
                result += range
            }
            result
        }
    }

    private infix fun IntRange.intersects(other: IntRange): Boolean =
        start in other || endInclusive in other || other.start in this || other.endInclusive in this

    private fun TryCatchBlockNode.diagnostic(index: Int = tryCatchBlocks.indexOf(this)): String =
        "#$index:$range->${handler.index}($type)"

    private val TryCatchBlockNode.range: IntRange
        get() = start.index until end.index

    private fun TryCatchBlockNode.copy(
        start: LabelNode = this.start,
        end: LabelNode = this.end,
        handler: LabelNode = this.handler
    ) = TryCatchBlockNode(start, end, handler, type).also { newTryCatch ->
        newTryCatch.visibleTypeAnnotations = visibleTypeAnnotations
        newTryCatch.invisibleTypeAnnotations = invisibleTypeAnnotations
    }

    /**
     * Split the receiving try-catch into two at the given [middleIndex].  If provided, set the first catch's handler
     * to [firstHandler] and the second to [secondHandler].
     */
    private fun TryCatchBlockNode.splitTryCatch(
        middleIndex: Int,
        firstHandler: AbstractInsnNode? = null,
        secondHandler: AbstractInsnNode? = null
    ): TryCatchBlockNode {

        val middle = findOrCreateLabel(Before(graph.instructions[middleIndex]))

        return copy(start = middle).also { secondTryCatch ->
            val origDiagnostic = if (log.isDebugEnabled) diagnostic() else ""
            end = middle
            if (firstHandler != null) handler = firstHandler as LabelNode
            if (secondHandler != null) secondTryCatch.handler = secondHandler as LabelNode
            log.debug {
                "Split try-catch $origDiagnostic into ${diagnostic()} and " +
                        secondTryCatch.diagnostic(tryCatchBlocks.indexOf(this) + 1)
            }
        }
    }

    /**
     * Another type of entrypoint we need to handle comes from try-catches.  If we cloned a "catch",
     * then we need to update or split the corresponding "try" so that instructions in the "owner" region
     * are handled by the clone of the "catch" while other instructions are handled by the original "catch".
     */
    private fun splitTryCatches() {
        // find any try-catch entries where we cloned the catch handler
        val tryCatchItr = tryCatchBlocks.listIterator()
        while (tryCatchItr.hasNext()) {
            val tryCatch = tryCatchItr.next()
            log.debug { "Checking if we need to split ${tryCatch.diagnostic()}" }
            // nothing to do if we didn't clone the handler
            val clonedHandler = clones[tryCatch.handler.instruction] ?: continue

            // five cases:
            // 1. ownedBy entirely contains tryCatchRange - no splitting needed, just update the handler
            // 2. ownedByRange and tryCatchRanges are disjoint - nothing to do
            // 3. ownedByRange start matches or starts outside and ends inside tryCatchRange
            // 4. ownedByRange starts inside and end matches or ends outside tryCatchRange
            // 5. ownedByRange entirely contained within tryCatchRange and neither start nor end match
            if (ownedBy.map { it.index }.containsAll(tryCatch.range.toSet())) {
                // case 1
                val oldHandler = tryCatch.handler
                tryCatch.handler = clonedHandler.asm as LabelNode
                log.debug { "Updated catch block for try-catch ${tryCatch.diagnostic()} from ${oldHandler.instruction}" }
            } else {
                // sorted to minimize the number of ranges; reversed so labels added don't change subsequent ranges
                val ownedByRanges = ownedBy.sortedBy { it.index }.toIndexRanges().reversed()
                for (ownedByRange in ownedByRanges) {
                    // case 2 - nothing to do
                    if (!ownedByRange.intersects(tryCatch.range)) {
                        log.trace { "$ownedByRange does not intersect ${tryCatch.diagnostic()}" }
                        continue
                    }
                    log.debug { "Need to split try-catch ${tryCatch.diagnostic()} for $ownedByRange" }
                    val origHandler = tryCatch.handler
                    var splitAlready = false
                    if (ownedByRange.endInclusive in tryCatch.range &&
                        ownedByRange.endInclusive != tryCatch.range.endInclusive) {
                        // case 3 or case 5, split into two catches:
                        // tryCatchRange.start..ownedByRange.endInclusive - cloned handler
                        // ownedByRange.endInclusive..tryCatchRange.endInclusive - original handler
                        val middleIndex = ownedByRange.endInclusive + 1
                        tryCatchItr.add(tryCatch.splitTryCatch(middleIndex, clonedHandler.asm))
                        splitAlready = true
                        // make it so the next() is the new new try-catch
                        tryCatchItr.previous()
                        tryCatchItr.previous()
                        // for case 5, need to fall through and split the new try-catch a second time
                    }
                    if (ownedByRange.start in tryCatch.range && ownedByRange.start != tryCatch.range.start) {
                        // case 4 or case 5, split into two catches:
                        // tryCatchRange.start..ownedByRange.start - original handler
                        // ownedByRange.start..tryCatchRange.endInclusive - cloned handler
                        tryCatchItr.add(tryCatch.splitTryCatch(ownedByRange.start, origHandler, clonedHandler.asm))
                        // make it so the next() is the new new try-catch
                        tryCatchItr.previous()
                        if (!splitAlready) tryCatchItr.previous()
                    }

                    // for case 5, both of the above matched resulting in three catches:
                    // tryCatchRange.start..ownedByRange.start - original handler
                    // ownedByRange.start..ownedByRange.endInclusive - cloned handler
                    // ownedByRange.endInclusive..tryCatchRange.endInclusive - original handler
                }
            }
        }
    }

    /**
     * If any of the original instructions are covered by "try"s, then create copies of the try-catch entries so that
     * exceptions raised by the cloned instructions are also handled by the corresponding "catch".
     */
    private fun cloneTryCatches() {
        // find any try-catch entries that cover instructions that we cloned, and also clone the try-catch entry
        var clonedIndexRanges = toClone.toIndexRanges()
        for (clonedIndexRangeIndex in 0 until clonedIndexRanges.size) {
            var clonedIndexRange = clonedIndexRanges[clonedIndexRangeIndex]
            val tryCatchItr = tryCatchBlocks.listIterator()
            while (tryCatchItr.hasNext()) {
                val tryCatch = tryCatchItr.next()
                if (tryCatch.range intersects clonedIndexRange) {
                    log.debug { "Clone instructions $clonedIndexRange are covered by try-catch ${tryCatch.diagnostic()}" }

                    val startIndex = clonedIndexRange.first { it in tryCatch.range }
                    val newStart = findOrCreateLabel(Before(clones.getValue(graph.instructions[startIndex]))) {
                        clonedIndexRanges = toClone.toIndexRanges()
                        clonedIndexRange = clonedIndexRanges[clonedIndexRangeIndex]
                    }

                    val endIndex = clonedIndexRange.last { it in tryCatch.range }
                    val newEnd = findOrCreateLabel(After(clones.getValue(graph.instructions[endIndex]))) {
                        clonedIndexRanges = toClone.toIndexRanges()
                        clonedIndexRange = clonedIndexRanges[clonedIndexRangeIndex]
                    }

                    val newHandler = if (toClone.any { it.asm === tryCatch.handler }) {
                        log.trace("handler part of cloned vertices, so using clone")
                        clonedLabels.getValue(tryCatch.handler)
                    } else {
                        log.trace("handler not part of cloned instructions")
                        tryCatch.handler
                    }

                    val newTryCatch = tryCatch.copy(newStart, newEnd, newHandler)
                    tryCatchItr.add(newTryCatch)
                    log.debug { "Added new try-catch ${newTryCatch.diagnostic()} as clone of ${tryCatch.diagnostic()}" }
                }
            }
        }
    }

    private fun verify() {
        // this causes NPEs from ASM, so it's easier if we catch it here
        for (tryCatch in tryCatchBlocks) {
            log.trace(tryCatch.diagnostic())
            check(tryCatch.start.index < tryCatch.end.index) {
                "Found malformed try-catch: ${tryCatch.diagnostic()}"
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger("com.hpe.kraal.InstructionCloner")
    }
}
