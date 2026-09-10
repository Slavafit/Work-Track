package com.example.worktrack

import org.junit.Assert.*
import org.junit.Test

class AmountRulesTest {
    @Test fun `decimal euros parse exactly and maximum cents round trip`() {
        assertEquals(1250L, parseAmount("12,50"))
        assertEquals(1250L, parseAmount("12.50"))
        assertEquals(1205L, parseAmount("12.05"))
        assertEquals(1250L, parseAmount("12.5"))
        assertEquals(1L, parseAmount("0.01"))
        assertEquals(Long.MAX_VALUE, parseAmount(Long.MAX_VALUE.amountInput()))
        assertNull(parseAmount("92233720368547758.08"))
        assertEquals("12,50 €", 1250L.money(java.util.Locale("es", "ES")))
        assertEquals("12.50 €", 1250L.money(java.util.Locale.US))
    }
    @Test fun `zero is valid but missing input is not`() {
        assertEquals(0L, parseAmount("0"))
        assertNull(parseAmount(""))
        assertNull(parseAmount("  "))
    }

    @Test fun `pasted decimal negative and formatted amounts are never reinterpreted`() {
        listOf("12,500", "12.500", "12,", "12.", "1,2.3", "-100", "+100", "1 000", "1e3", "12€").forEach { assertNull(it, parseAmount(it)) }
        assertEquals(125000L, parseAmount(" 1250 "))
    }

    @Test fun `overflow is rejected for individual amounts and totals`() {
        assertNull(parseAmount("9223372036854775808"))
        assertNull(checkedAmountTotal(listOf(Long.MAX_VALUE, 1)))
        assertNull(checkedAmountTotal(listOf(10, -1)))
        assertEquals(420L, checkedAmountTotal(listOf(100, 200, 50, 70)))
    }
}
