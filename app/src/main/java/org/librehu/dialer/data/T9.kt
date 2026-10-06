package org.librehu.dialer.data

import java.text.Normalizer
import java.util.Locale

/**
 * T9 search: the digits typed on the keypad stand for the letters printed on the keys (2 = ABC … 9 = WXYZ). A contact
 * matches when the digits spell the start of one of its words ("Dupont" for 3876), the start of the name from one of
 * its words on ("Jean Dupont" for 5326), or its initials (53 for "Jean Dupont").
 */
object T9 {
    private val LETTERS =
        mapOf(
            '2' to "ABC",
            '3' to "DEF",
            '4' to "GHI",
            '5' to "JKL",
            '6' to "MNO",
            '7' to "PQRS",
            '8' to "TUV",
            '9' to "WXYZ",
            '0' to " ",
        )
    private val DIGIT: Map<Char, Char> = LETTERS.flatMap { (d, l) -> l.map { it to d } }.toMap()

    /** Digits of [name] key by key ('1' for what is not on a key); words separated by spaces. */
    fun encode(name: String): String =
        Normalizer
            .normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .uppercase(Locale.ROOT)
            .map { c ->
                if (c.isWhitespace() || c == '-' || c == '\'') {
                    ' '
                } else if (c.isDigit()) {
                    c
                } else {
                    DIGIT[c] ?: '1'
                }
            }.joinToString("")
            .trim()
            .replace(Regex(" +"), " ")

    /** 0 = no match, higher is better (start of the name > start of a word > initials). */
    fun score(
        name: String,
        digits: String,
    ): Int {
        if (digits.isEmpty() || digits.any { !it.isDigit() }) return 0
        val words = encode(name).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return 0
        for (i in words.indices) {
            val joined = words.drop(i).joinToString("")
            if (joined.startsWith(digits)) return if (i == 0) 3 else 2
        }
        if (digits.length >= 2 && words.joinToString("") { it.take(1) }.startsWith(digits)) return 1
        return 0
    }
}
