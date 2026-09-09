package com.example.worktrack

import org.junit.Assert.*
import org.junit.Test

class AmountRulesTest {
    @Test fun `zero is valid but missing input is not`() {
        assertEquals(0L, parseAmount("0"))
        assertNull(parseAmount(""))
        assertNull(parseAmount("  "))
    }

    @Test fun `pasted decimal negative and formatted amounts are never reinterpreted`() {
        listOf("12,50", "12.50", "-100", "+100", "1 000", "1e3", "12€").forEach { assertNull(it, parseAmount(it)) }
        assertEquals(1250L, parseAmount(" 1250 "))
    }

    @Test fun `overflow is rejected for individual amounts and totals`() {
        assertNull(parseAmount("9223372036854775808"))
        assertNull(checkedAmountTotal(listOf(Long.MAX_VALUE, 1)))
        assertNull(checkedAmountTotal(listOf(10, -1)))
        assertEquals(420L, checkedAmountTotal(listOf(100, 200, 50, 70)))
    }
}
