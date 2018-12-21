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

import com.hpe.kraal.realloops.LoopInCatch
import com.hpe.kraal.realloops.MultipleYieldsInLoop
import com.hpe.kraal.realloops.NestedTryCatchWithExceptions
import com.hpe.kraal.realloops.RealLoop
import com.hpe.kraal.realloops.SimpleMultiEntryLoop
import com.hpe.kraal.realloops.TryCatchInLoop
import com.hpe.kraal.realloops.TryCatchNestedLoops
import com.hpe.kraal.realloops.TryCatchWithException
import com.hpe.kraal.realloops.TryFinallyInLoop
import com.hpe.kraal.realloops.TwoMultiEntryLoops
import com.hpe.kraal.realloops.WhenInLoop
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test that processes the classes in the realloops package and compares the result of executing the processed version
 * to the unprocessed version.
 */
class RealLoopsTest {

    /**
     * ClassLoader to load classes from in-memory ASM ClassNode objects.
     */
    private inner class ClassNodeLoader(private val nodes: Collection<ClassNode>) : ClassLoader(contextClassLoader) {

        // to help make sure we don't whiff and end up verifying the behavior of the unprocessed classes
        var loadedModifiedClass = false
            private set

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            val alreadyLoaded = findLoadedClass(name)
            if (alreadyLoaded != null) return alreadyLoaded
            val classNode = nodes.find { name == it.name.replace('/', '.') }
            return if (classNode != null) {
                val bytes = classNode.toByteArray()
                defineClass(name, bytes, 0, bytes.size).also { newClass ->
                    loadedModifiedClass = true
                    if (resolve) resolveClass(newClass)
                }
            } else {
                super.loadClass(name, resolve)
            }
        }
    }

    /**
     * Alias for Thread.currentThread().contextClassLoader
     */
    private var contextClassLoader
        get() = Thread.currentThread().contextClassLoader
        set(value) {
            Thread.currentThread().contextClassLoader = value
        }

    /**
     * Execute a [block] with the given [classloader] as the context classloader, then restore the context classloader
     * to its original value.
     */
    private inline fun <C : ClassLoader, T> withClassloader(classloader: C, block: C.() -> T): T {
        val oldContextClassloader = contextClassLoader
        try {
            contextClassLoader = classloader
            return classloader.block()
        } finally {
            contextClassLoader = oldContextClassloader
        }
    }

    /**
     * Process the classes associated with the given [unprocessed] test loop instance and call the processed version of
     * its [RealLoop.test] method.
     */
    private fun callProcessedTestMethod(unprocessed: RealLoop): Sequence<String> {
        var someClassModified = false // to help make sure we don't whiff and end up verifying *unmodified* bytecode
        val names = listOf(unprocessed::class.qualifiedName!!, "${unprocessed::class.qualifiedName}\$test\$1")
        val nodes = names.map { name ->
            val node = ClassReader(name).toClassNode()
            if (removeIrreducibleLoops(node)) {
                someClassModified = true
                assertFalse(removeIrreducibleLoops(node), "2nd call to removeIrreducibleLoops() should return false")
            }
            node
        }

        assertTrue(someClassModified, "No classes modified when processing ${unprocessed::class}")

        return withClassloader(ClassNodeLoader(nodes)) {
            val kClass = loadClass(names.first()).kotlin
            val instance = kClass.primaryConstructor!!.call() as RealLoop
            assertTrue(
                loadedModifiedClass,
                "No classes loaded from custom loader when processing ${unprocessed::class}"
            )
            instance.test()
        }
    }

    /**
     * Verify that the processed result of executing the given [unprocessed] code matches the result of executing the
     * unprocessed version.
     */
    private fun testProcessed(unprocessed: RealLoop) {
        val actual = callProcessedTestMethod(unprocessed)
        assertEquals(unprocessed.test().toList(), actual.toList())
    }

    @Test
    fun `test processed version of SimpleMultiEntryLoop`() = testProcessed(SimpleMultiEntryLoop())

    @Test
    fun `test processed version of MultipleYieldsInLoop`() = testProcessed(MultipleYieldsInLoop())

    @Test
    fun `test processed version of TwoMultiEntryLoops`() = testProcessed(TwoMultiEntryLoops())

    @Test
    fun `test processed version of TryCatchInLoop`() = testProcessed(TryCatchInLoop())

    @Test
    fun `test processed version of TryCatchWithException`() = testProcessed(TryCatchWithException())

    @Test
    fun `test processed version of TryCatchNestedLoops`() = testProcessed(TryCatchNestedLoops())

    @Test
    fun `test processed version of LoopInCatch`() = testProcessed(LoopInCatch())

    @Test
    fun `test processed version of WhenInLoop`() = testProcessed(WhenInLoop())

    @Test
    fun `test processed version of TryFinallyInLoop`() = testProcessed(TryFinallyInLoop())

    @Test
    fun `test processed version of NestedTryCatchWithExceptions`() = testProcessed(NestedTryCatchWithExceptions())
}