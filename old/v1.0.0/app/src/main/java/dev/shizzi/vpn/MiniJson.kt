package dev.shizzi.vpn

/**
 * A deliberately small JSON reader/writer with no dependencies.
 *
 * It exists so [ConfigParser] and [XrayConfigBuilder] are plain Kotlin that runs
 * on a bare JVM: org.json is an Android framework class and is absent from unit
 * tests unless pulled in separately, which would make the parser untestable
 * without a device.
 *
 * Values are Map<String, Any?>, List<Any?>, String, Long/Double, Boolean or null.
 */
internal object MiniJson {

    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Boolean -> out.append(value)
            is Int, is Long, is Short, is Byte -> out.append(value.toString())
            is Double -> out.append(if (value % 1.0 == 0.0) value.toLong().toString() else value.toString())
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) out.append(',')
                    first = false
                    writeString(out, k.toString())
                    out.append(':')
                    write(out, v)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (v in value) {
                    if (!first) out.append(',')
                    first = false
                    write(out, v)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("unsupported JSON value: ${value::class}")
        }
    }

    private fun writeString(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.readValue()
        reader.skipWhitespace()
        require(reader.atEnd()) { "trailing characters after JSON value" }
        return value
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> =
        parse(text) as? Map<String, Any?> ?: throw IllegalArgumentException("not a JSON object")

    private class Reader(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun skipWhitespace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun readValue(): Any? {
            skipWhitespace()
            require(i < s.length) { "unexpected end of JSON" }
            return when (val c = s[i]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else throw IllegalArgumentException("unexpected '$c' at $i")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            require(s.startsWith(word, i)) { "bad literal at $i" }
            i += word.length
            return value
        }

        private fun readNumber(): Any {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val token = s.substring(start, i)
            return token.toLongOrNull() ?: token.toDouble()
        }

        private fun readString(): String {
            i++ // opening quote
            val out = StringBuilder()
            while (true) {
                require(i < s.length) { "unterminated string" }
                val c = s[i++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        require(i < s.length) { "dangling escape" }
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> out.append(e)
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'u' -> {
                                require(i + 4 <= s.length) { "short \\u escape" }
                                out.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("bad escape \\$e")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun readArray(): List<Any?> {
            i++ // [
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (s.getOrNull(i) == ']') { i++; return list }
            while (true) {
                list += readValue()
                skipWhitespace()
                when (s.getOrNull(i++)) {
                    ',' -> continue
                    ']' -> return list
                    else -> throw IllegalArgumentException("expected , or ] at ${i - 1}")
                }
            }
        }

        private fun readObject(): Map<String, Any?> {
            i++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (s.getOrNull(i) == '}') { i++; return map }
            while (true) {
                skipWhitespace()
                require(s.getOrNull(i) == '"') { "expected key at $i" }
                val key = readString()
                skipWhitespace()
                require(s.getOrNull(i++) == ':') { "expected : at ${i - 1}" }
                map[key] = readValue()
                skipWhitespace()
                when (s.getOrNull(i++)) {
                    ',' -> continue
                    '}' -> return map
                    else -> throw IllegalArgumentException("expected , or } at ${i - 1}")
                }
            }
        }
    }
}
