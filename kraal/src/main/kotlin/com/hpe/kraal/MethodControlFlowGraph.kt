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
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue

/**
 * Wraps an ASM AbstractInsnNode and implements Vertex to allow it to be inserted into a Digraph.
 *
 * We keep track of edges from try-catch blocks separately from "normal" control flow, because these edges are handled
 * differently by InstructionCloner.
 */
internal class Instruction(
    val asm: AbstractInsnNode,
    private val instructionList: InsnList,
    val successors: MutableSet<Instruction> = mutableSetOf(),
    val predecessors: MutableSet<Instruction> = mutableSetOf(),
    val catchSuccessors: MutableSet<Instruction> = mutableSetOf(),
    val tryPredecessors: MutableSet<Instruction> = mutableSetOf()
) : Vertex<Instruction> {
    constructor(index: Int, instructionList: InsnList) : this(instructionList[index], instructionList)

    override fun toString() = asm.toUsefulString(index)

    override val index: Int
        get() = instructionList.indexOf(asm)
}

/**
 * A Digraph representing the control flow of a method.
 */
internal class MethodControlFlowGraph(
    val instructions: MutableList<Instruction>,
    val classNode: ClassNode,
    val method: MethodNode,
    var guaranteedAcyclic: Boolean = false,
    override val root: Instruction = instructions.first()
) : RootedDigraph<Instruction> {
    override val vertices = instructions
    // for generic digraph functions, combine the "regular" edges and the edges from try-catch blocks
    override fun successorsOf(vertex: Instruction) = vertex.successors + vertex.catchSuccessors
    override fun predecessorsOf(vertex: Instruction) = vertex.predecessors + vertex.tryPredecessors
    override val name: String = "${classNode.name}.${method.name}"
}

private fun MethodControlFlowGraph.createEdges() {
    guaranteedAcyclic = true

    val analyzer = object : Analyzer<BasicValue>(BasicInterpreter()) {
        override fun newControlFlowEdge(src: Int, dest: Int) {
            // If every control flow edge takes us deeper into the method, then the CFG is acyclic
            // If there is a jump backwards, then there *may* be a loop
            // This is a quick heuristic that lets us skip further processing on simple loop-free methods
            if (src > dest) guaranteedAcyclic = false

            vertices[src].successors += vertices[dest]
            vertices[dest].predecessors += vertices[src]
        }

        override fun newControlFlowExceptionEdge(src: Int, dest: Int): Boolean {
            if (src > dest) guaranteedAcyclic = false

            vertices[src].catchSuccessors += vertices[dest]
            vertices[dest].tryPredecessors += vertices[src]
            return true
        }
    }
    analyzer.analyze(classNode.name, method)
}

internal fun MethodNode.computeControlFlowGraph(classNode: ClassNode): MethodControlFlowGraph {
    val instructionNodes = (0 until instructions.size()).map { Instruction(it, instructions) }
    return MethodControlFlowGraph(instructionNodes.toMutableList(), classNode, this).apply {
        createEdges()
    }
}

internal fun MethodControlFlowGraph.resetEdges() {

    for (vertex in vertices) {
        vertex.successors.clear()
        vertex.predecessors.clear()
        vertex.tryPredecessors.clear()
        vertex.catchSuccessors.clear()
    }

    createEdges()
}

// TODO: make this not suck?
/**
 * ASM's AbstractInsnNode.toString() is not very useful.
 */
@Suppress("LongMethod")
private fun AbstractInsnNode.toUsefulString(index: Int) = when (opcode) {
    -1 -> {
        when (this) {
            is LabelNode -> "$label"
            is FrameNode -> "FRAME" // TODO
            is LineNumberNode -> "LINE$line"
            else -> error("unexpected type ${this::class}")
        }
    }
    Opcodes.NOP -> "NOP"
    Opcodes.ACONST_NULL -> "ACONST_NULL"
    Opcodes.ICONST_M1 -> "ICONST_M1"
    Opcodes.ICONST_0 -> "ICONST_0"
    Opcodes.ICONST_1 -> "ICONST_1"
    Opcodes.ICONST_2 -> "ICONST_2"
    Opcodes.ICONST_3 -> "ICONST_3"
    Opcodes.ICONST_4 -> "ICONST_4"
    Opcodes.ICONST_5 -> "ICONST_5"
    Opcodes.LCONST_0 -> "LCONST_0"
    Opcodes.LCONST_1 -> "LCONST_1"
    Opcodes.FCONST_0 -> "FCONST_0"
    Opcodes.FCONST_1 -> "FCONST_1"
    Opcodes.FCONST_2 -> "FCONST_2"
    Opcodes.DCONST_0 -> "DCONST_0"
    Opcodes.DCONST_1 -> "DCONST_1"
    Opcodes.BIPUSH -> "BIPUSH"
    Opcodes.SIPUSH -> "SIPUSH"
    Opcodes.LDC -> "LDC"
    Opcodes.ILOAD -> "ILOAD"
    Opcodes.LLOAD -> "LLOAD"
    Opcodes.FLOAD -> "FLOAD"
    Opcodes.DLOAD -> "DLOAD"
    Opcodes.ALOAD -> "ALOAD"
    Opcodes.IALOAD -> "IALOAD"
    Opcodes.LALOAD -> "LALOAD"
    Opcodes.FALOAD -> "FALOAD"
    Opcodes.DALOAD -> "DALOAD"
    Opcodes.AALOAD -> "AALOAD"
    Opcodes.BALOAD -> "BALOAD"
    Opcodes.CALOAD -> "CALOAD"
    Opcodes.SALOAD -> "SALOAD"
    Opcodes.ISTORE -> "ISTORE"
    Opcodes.LSTORE -> "LSTORE"
    Opcodes.FSTORE -> "FSTORE"
    Opcodes.DSTORE -> "DSTORE"
    Opcodes.ASTORE -> "ASTORE"
    Opcodes.IASTORE -> "IASTORE"
    Opcodes.LASTORE -> "LASTORE"
    Opcodes.FASTORE -> "FASTORE"
    Opcodes.DASTORE -> "DASTORE"
    Opcodes.AASTORE -> "AASTORE"
    Opcodes.BASTORE -> "BASTORE"
    Opcodes.CASTORE -> "CASTORE"
    Opcodes.SASTORE -> "SASTORE"
    Opcodes.POP -> "POP"
    Opcodes.POP2 -> "POP2"
    Opcodes.DUP -> "DUP"
    Opcodes.DUP_X1 -> "DUP_X1"
    Opcodes.DUP_X2 -> "DUP_X2"
    Opcodes.DUP2 -> "DUP2"
    Opcodes.DUP2_X1 -> "DUP2_X1"
    Opcodes.DUP2_X2 -> "DUP2_X2"
    Opcodes.SWAP -> "SWAP"
    Opcodes.IADD -> "IADD"
    Opcodes.LADD -> "LADD"
    Opcodes.FADD -> "FADD"
    Opcodes.DADD -> "DADD"
    Opcodes.ISUB -> "ISUB"
    Opcodes.LSUB -> "LSUB"
    Opcodes.FSUB -> "FSUB"
    Opcodes.DSUB -> "DSUB"
    Opcodes.IMUL -> "IMUL"
    Opcodes.LMUL -> "LMUL"
    Opcodes.FMUL -> "FMUL"
    Opcodes.DMUL -> "DMUL"
    Opcodes.IDIV -> "IDIV"
    Opcodes.LDIV -> "LDIV"
    Opcodes.FDIV -> "FDIV"
    Opcodes.DDIV -> "DDIV"
    Opcodes.IREM -> "IREM"
    Opcodes.LREM -> "LREM"
    Opcodes.FREM -> "FREM"
    Opcodes.DREM -> "DREM"
    Opcodes.INEG -> "INEG"
    Opcodes.LNEG -> "LNEG"
    Opcodes.FNEG -> "FNEG"
    Opcodes.DNEG -> "DNEG"
    Opcodes.ISHL -> "ISHL"
    Opcodes.LSHL -> "LSHL"
    Opcodes.ISHR -> "ISHR"
    Opcodes.LSHR -> "LSHR"
    Opcodes.IUSHR -> "IUSHR"
    Opcodes.LUSHR -> "LUSHR"
    Opcodes.IAND -> "IAND"
    Opcodes.LAND -> "LAND"
    Opcodes.IOR -> "IOR"
    Opcodes.LOR -> "LOR"
    Opcodes.IXOR -> "IXOR"
    Opcodes.LXOR -> "LXOR"
    Opcodes.IINC -> "IINC"
    Opcodes.I2L -> "I2L"
    Opcodes.I2F -> "I2F"
    Opcodes.I2D -> "I2D"
    Opcodes.L2I -> "L2I"
    Opcodes.L2F -> "L2F"
    Opcodes.L2D -> "L2D"
    Opcodes.F2I -> "F2I"
    Opcodes.F2L -> "F2L"
    Opcodes.F2D -> "F2D"
    Opcodes.D2I -> "D2I"
    Opcodes.D2L -> "D2L"
    Opcodes.D2F -> "D2F"
    Opcodes.I2B -> "I2B"
    Opcodes.I2C -> "I2C"
    Opcodes.I2S -> "I2S"
    Opcodes.LCMP -> "LCMP"
    Opcodes.FCMPL -> "FCMPL"
    Opcodes.FCMPG -> "FCMPG"
    Opcodes.DCMPL -> "DCMPL"
    Opcodes.DCMPG -> "DCMPG"
    Opcodes.IFEQ -> "IFEQ"
    Opcodes.IFNE -> "IFNE"
    Opcodes.IFLT -> "IFLT"
    Opcodes.IFGE -> "IFGE"
    Opcodes.IFGT -> "IFGT"
    Opcodes.IFLE -> "IFLE"
    Opcodes.IF_ICMPEQ -> "IF_ICMPEQ"
    Opcodes.IF_ICMPNE -> "IF_ICMPNE"
    Opcodes.IF_ICMPLT -> "IF_ICMPLT"
    Opcodes.IF_ICMPGE -> "IF_ICMPGE"
    Opcodes.IF_ICMPGT -> "IF_ICMPGT"
    Opcodes.IF_ICMPLE -> "IF_ICMPLE"
    Opcodes.IF_ACMPEQ -> "IF_ACMPEQ"
    Opcodes.IF_ACMPNE -> "IF_ACMPNE"
    Opcodes.GOTO -> "GOTO"
    Opcodes.JSR -> "JSR"
    Opcodes.RET -> "RET"
    Opcodes.TABLESWITCH -> "TABLESWITCH"
    Opcodes.LOOKUPSWITCH -> "LOOKUPSWITCH"
    Opcodes.IRETURN -> "IRETURN"
    Opcodes.LRETURN -> "LRETURN"
    Opcodes.FRETURN -> "FRETURN"
    Opcodes.DRETURN -> "DRETURN"
    Opcodes.ARETURN -> "ARETURN"
    Opcodes.RETURN -> "RETURN"
    Opcodes.GETSTATIC -> "GETSTATIC"
    Opcodes.PUTSTATIC -> "PUTSTATIC"
    Opcodes.GETFIELD -> "GETFIELD"
    Opcodes.PUTFIELD -> "PUTFIELD"
    Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL"
    Opcodes.INVOKESPECIAL -> "INVOKESPECIAL"
    Opcodes.INVOKESTATIC -> "INVOKESTATIC"
    Opcodes.INVOKEINTERFACE -> "INVOKEINTERFACE"
    Opcodes.INVOKEDYNAMIC -> "INVOKEDYNAMIC"
    Opcodes.NEW -> "NEW"
    Opcodes.NEWARRAY -> "NEWARRAY"
    Opcodes.ANEWARRAY -> "ANEWARRAY"
    Opcodes.ARRAYLENGTH -> "ARRAYLENGTH"
    Opcodes.ATHROW -> "ATHROW"
    Opcodes.CHECKCAST -> "CHECKCAST"
    Opcodes.INSTANCEOF -> "INSTANCEOF"
    Opcodes.MONITORENTER -> "MONITORENTER"
    Opcodes.MONITOREXIT -> "MONITOREXIT"
    Opcodes.MULTIANEWARRAY -> "MULTIANEWARRAY"
    Opcodes.IFNULL -> "IFNULL"
    Opcodes.IFNONNULL -> "IFNONNULL"
    else -> error("Unknown opcode $this")
} + "($index)"