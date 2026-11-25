package com.chinmay.voicefiles

import java.util.Locale

object TextNorm {

    private val CAMEL = Regex("([a-z])([A-Z])")
    private val LETTER_DIGIT = Regex("(\\p{L})(\\p{N})")
    private val DIGIT_LETTER = Regex("(\\p{N})(\\p{L})")
    private val SPLIT = Regex("[^\\p{L}\\p{N}]+")

    /**
     * "PhysicsNotes_ch3 (1)" → [physics, notes, ch, 3, 1]
     * Splits on punctuation, camelCase and letter/digit boundaries; numbers lose leading zeros.
     */
    fun tokenize(raw: String): List<String> {
        val spaced = raw
            .replace(CAMEL, "$1 $2")
            .replace(LETTER_DIGIT, "$1 $2")
            .replace(DIGIT_LETTER, "$1 $2")
        return spaced.lowercase(Locale.ROOT)
            .split(SPLIT)
            .filter { it.isNotEmpty() }
            .map { if (it.isDigits()) it.trimStart('0').ifEmpty { "0" } else it }
    }

    fun String.isDigits(): Boolean = isNotEmpty() && all { it.isDigit() }

    /** Similarity of one spoken word to one file-name word, 0.0 … 1.0. */
    fun tokenSim(q: String, n: String): Double {
        if (q == n) return 1.0
        if (q.isDigits() || n.isDigits()) return 0.0          // numbers must match exactly
        if (q.length >= 3 && n.startsWith(q)) return 0.85     // "phys" → "physics"
        if (n.length >= 3 && q.startsWith(n)) return 0.75     // "assignments" → "assignment", "physics" → "phy"
        if (minOf(q.length, n.length) >= 4) {
            val sim = 1.0 - levenshtein(q, n).toDouble() / maxOf(q.length, n.length)
            if (sim >= 0.7) return sim * 0.85                  // small mis-hearing / typo
        }
        return 0.0
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
