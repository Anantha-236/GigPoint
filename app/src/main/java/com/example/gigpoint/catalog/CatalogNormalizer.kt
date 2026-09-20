package com.example.gigpoint.catalog

import java.text.Normalizer
import java.util.Locale

object CatalogNormalizer {
    private val whitespace = Regex("""\s+""")
    private val punctuation = Regex("""[^\p{L}\p{N}.₹]+""")

    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace("-", " ")
            .replace(punctuation, " ")
            .replace(whitespace, " ")
            .trim()

    fun tokens(value: String): Set<String> =
        normalize(value)
            .split(" ")
            .filter { it.length >= 2 }
            .toSet()

    fun similarity(left: String, right: String): Double {
        val ln = normalize(left)
        val rn = normalize(right)

        if (ln.isBlank() || rn.isBlank()) return 0.0
        if (ln == rn) return 1.0
        if (ln.contains(rn) || rn.contains(ln)) return 0.92

        val a = tokens(left)
        val b = tokens(right)
        if (a.isEmpty() || b.isEmpty()) return 0.0

        return a.intersect(b).size.toDouble() /
            a.union(b).size.toDouble()
    }
}
