package com.example.worktrack

class InvalidAmountException : IllegalArgumentException("Amount or total is out of range")

/** UI values are euros; database values are integer euro cents. No rounding is allowed. */
fun parseAmount(input: String): Long? {
    val value = input.trim()
    if (!Regex("[0-9]+([.,][0-9]{1,2})?").matches(value)) return null
    return try {
        java.math.BigDecimal(value.replace(',', '.')).movePointRight(2).longValueExact()
    } catch (_: ArithmeticException) { null }
}

fun Long.amountInput(): String = java.math.BigDecimal.valueOf(this, 2).toPlainString()

fun checkedAmountTotal(amounts: Iterable<Long>): Long? {
    var total = 0L
    for (amount in amounts) {
        if (amount < 0 || amount > Long.MAX_VALUE - total) return null
        total += amount
    }
    return total
}
