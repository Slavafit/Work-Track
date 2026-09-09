package com.example.worktrack

class InvalidAmountException : IllegalArgumentException("Amount or total is out of range")

/** Existing database amounts are whole euros. Never strip punctuation from money input. */
fun parseAmount(input: String): Long? {
    val value = input.trim()
    if (value.isEmpty() || value.any { it !in '0'..'9' }) return null
    return value.toLongOrNull()?.takeIf { it >= 0 }
}

fun checkedAmountTotal(amounts: Iterable<Long>): Long? {
    var total = 0L
    for (amount in amounts) {
        if (amount < 0 || amount > Long.MAX_VALUE - total) return null
        total += amount
    }
    return total
}
