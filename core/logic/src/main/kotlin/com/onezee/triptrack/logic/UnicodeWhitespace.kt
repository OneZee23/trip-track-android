package com.onezee.triptrack.logic

/**
 * Unicode White_Space, the set Swift's `.whitespacesAndNewlines` splits on.
 *
 * Spelled out because the two regex engines disagree: the JVM's `\s` is ASCII-only unless the
 * Unicode-class flag is set, and Android's ICU engine rejects that flag at compile time
 * (PatternSyntaxException), which crashed the Me tab on phones while every JVM test passed.
 */
object UnicodeWhitespace {
    val runs = Regex("[\\t\\n\\x0B\\f\\r\\p{Z}\\u0085]+")
}
