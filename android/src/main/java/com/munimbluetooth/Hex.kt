package com.munimbluetooth

/**
 * Table-driven hex codec. The previous `joinToString { "%02x".format(it) }`
 * ran String.format once per byte, which dominated the cost of every
 * notification, scan record and stream chunk that crossed to JS as hex.
 */
internal object Hex {
    private val DIGITS = "0123456789abcdef".toCharArray()

    fun encode(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): String {
        val out = CharArray(length * 2)
        var j = 0
        for (i in offset until offset + length) {
            val v = bytes[i].toInt() and 0xFF
            out[j++] = DIGITS[v ushr 4]
            out[j++] = DIGITS[v and 0x0F]
        }
        return String(out)
    }

    /**
     * Decodes hex (either case, spaces ignored). Returns null for an odd
     * number of digits or a non-hex character.
     */
    fun decode(hex: String): ByteArray? {
        var digits = 0
        for (c in hex) {
            if (c != ' ') digits++
        }
        if (digits % 2 != 0) return null
        val out = ByteArray(digits / 2)
        var high = -1
        var index = 0
        for (c in hex) {
            if (c == ' ') continue
            val nibble = nibble(c)
            if (nibble < 0) return null
            if (high < 0) {
                high = nibble
            } else {
                out[index++] = ((high shl 4) or nibble).toByte()
                high = -1
            }
        }
        return out
    }

    private fun nibble(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }
}
