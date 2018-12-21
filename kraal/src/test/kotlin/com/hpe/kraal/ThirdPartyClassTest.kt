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
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThirdPartyClassTest {
    private fun verifyProcessClass(className: String) {
        val classNode = ClassReader(className).toClassNode()
        // most of the test is actually performed by the verifications within removeIrreducibleLoops()...
        assertTrue(removeIrreducibleLoops(classNode))
        // but we can also write to a byte array in order to do verification with ASM,
        // since it is not performed by this overload of removeIrreducibleLoops()
        classNode.toByteArray()
        // finally, we can check that a second pass doesn't find any other irreducible loops:
        assertFalse(removeIrreducibleLoops(classNode))
    }

    @Test
    fun `removeIrreducibleLoops IOCoroutineDispatcher$IOThread$run$1`() {
        // TODO: IOCoroutineDispatcher is deprecated - but it's a great test case...
        verifyProcessClass("io.ktor.network.util.IOCoroutineDispatcher\$IOThread\$run\$1")
    }

    @Test
    fun `removeIrreducibleLoops CIOWriterKt$attachForWritingImpl$1`() {
        verifyProcessClass("io.ktor.network.sockets.CIOWriterKt\$attachForWritingImpl\$1")
    }

    @Test
    fun `removeIrreducibleLoops PipelineKt$startConnectionPipeline$2$outputsActor$1`() {
        verifyProcessClass("io.ktor.http.cio.PipelineKt\$startConnectionPipeline\$2\$outputsActor\$1")
    }

    @Test
    fun `removeIrreducibleLoops PipelineKt$startConnectionPipeline$2`() {
        verifyProcessClass("io.ktor.http.cio.PipelineKt\$startConnectionPipeline\$2")
    }

    @Test
    fun `removeIrreducibleLoops Sessions$Feature$install$1`() {
        verifyProcessClass("io.ktor.sessions.Sessions\$Feature\$install\$1")
    }

    @Test
    fun `removeIrreducibleLoops ActorSelectorManager`() {
        verifyProcessClass("io.ktor.network.selector.ActorSelectorManager")
    }

    @Test
    fun `removeIrreducibleLoops ByteBufferChannel`() {
        verifyProcessClass("kotlinx.coroutines.io.ByteBufferChannel")
    }

    @Test
    fun `removeIrreducibleLoops EndPointChannelsKt$endPointWriter$1`() {
        verifyProcessClass("io.ktor.server.jetty.internal.EndPointChannelsKt\$endPointWriter\$1")
    }
}