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

import org.slf4j.Logger

/**
 * Convenience method to use efficient Kotlin string formatting instead of SFL4J parameterized logging.
 *
 *     LOG.trace { "Hello, $name." }
 * The parsing of the format string is done by the Kotlin compiler, rather than by the log framework at runtime.
 * The string is built with a StringBuilder, only if trace logging is enabled.
 *
 * @param t optional exception to include in the log message
 * @param msg message producer, usually a lambda with a Kotlin string template
 */
inline fun Logger.trace(t: Throwable? = null, msg: () -> String) {
    if (isTraceEnabled) trace(msg(), t)
}

/**
 * Convenience method to use efficient Kotlin string formatting instead of SFL4J parameterized logging.
 *
 *     LOG.debug { "Hello, $name." }
 * The parsing of the format string is done by the Kotlin compiler, rather than by the log framework at runtime.
 * The string is built with a StringBuilder, only if debug logging is enabled.
 *
 * @param t optional exception to include in the log message
 * @param msg message producer, usually a lambda with a Kotlin string template
 */
inline fun Logger.debug(t: Throwable? = null, msg: () -> String) {
    if (isDebugEnabled) debug(msg(), t)
}

/**
 * Convenience method to use efficient Kotlin string formatting instead of SFL4J parameterized logging.
 *
 *     LOG.info { "Hello, $name." }
 * The parsing of the format string is done by the Kotlin compiler, rather than by the log framework at runtime.
 * The string is built with a StringBuilder, only if info logging is enabled.
 *
 * @param t optional exception to include in the log message
 * @param msg message producer, usually a lambda with a Kotlin string template
 */
inline fun Logger.info(t: Throwable? = null, msg: () -> String) {
    if (isInfoEnabled) info(msg(), t)
}

/**
 * Convenience method to use efficient Kotlin string formatting instead of SFL4J parameterized logging.
 *
 *     LOG.warn { "Hello, $name." }
 * The parsing of the format string is done by the Kotlin compiler, rather than by the log framework at runtime.
 * The string is built with a StringBuilder, only if warn logging is enabled.
 *
 * @param t optional exception to include in the log message
 * @param msg message producer, usually a lambda with a Kotlin string template
 */
inline fun Logger.warn(t: Throwable? = null, msg: () -> String) {
    if (isWarnEnabled) warn(msg(), t)
}

/**
 * Convenience method to use efficient Kotlin string formatting instead of SFL4J parameterized logging.
 *
 *     LOG.error { "Hello, $name." }
 * The parsing of the format string is done by the Kotlin compiler, rather than by the log framework at runtime.
 * The string is built with a StringBuilder, only if error logging is enabled.
 *
 * @param t optional exception to include in the log message
 * @param msg message producer, usually a lambda with a Kotlin string template
 */
inline fun Logger.error(t: Throwable? = null, msg: () -> String) {
    if (isErrorEnabled) error(msg(), t)
}
