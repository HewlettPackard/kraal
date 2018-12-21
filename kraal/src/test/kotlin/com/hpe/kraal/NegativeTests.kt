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

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Tests that verify classes without irreducible loops are left unmodified.
 */
class NegativeTests {
    private fun verifyDoesNotModifyClass(className: String) {
        verifyDoesNotModifyClass(ClassReader(className).toClassNode())
    }

    private fun verifyDoesNotModifyClass(classNode: ClassNode) {
        assertFalse(removeIrreducibleLoops(classNode))
    }

    @Test
    fun `does not modify typesafe config DefaultTransformer`() {
        verifyDoesNotModifyClass("com.typesafe.config.impl.DefaultTransformer")
    }

    @Test
    fun `does not modify jetty AbstractConnector`() {
        verifyDoesNotModifyClass("org.eclipse.jetty.server.AbstractConnector\$Acceptor")
    }

    @Test
    fun `does not modify empty class`() {
        // an empty method doesn't result in an empty instruction list, but somehow other code has them
        // so fake it in a unit test
        verifyDoesNotModifyClass(ClassNode())
    }

    @Test
    fun `does not modify empty method`() {
        // an empty method doesn't result in an empty instruction list, but somehow other code has them
        // so fake it in a unit test
        verifyDoesNotModifyClass(ClassNode().apply {
            methods.add(MethodNode())
        })
    }

    @Test
    fun `does not modify acyclic method`() {
        class AcyclicMethodTest {
            fun acyclic() = if (Random.nextBoolean()) "hi" else "bye"
        }

        verifyDoesNotModifyClass(AcyclicMethodTest::class.java.name)
    }
}