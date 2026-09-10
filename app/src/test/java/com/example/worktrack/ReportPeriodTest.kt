package com.example.worktrack

import com.example.worktrack.data.CustomerPayment
import com.example.worktrack.data.ObjectReportRow
import org.junit.Assert.*
import org.junit.Test

class ReportPeriodTest {
    @Test fun `period separates work materials and payments while carrying opening advance`() {
        val rows = listOf(
            ObjectReportRow(1, 10, 1, "Worker", "Old", 1000, null),
            ObjectReportRow(2, 20, 1, "Worker", "Work", 3000, null),
            ObjectReportRow(2, 20, 1, "Worker", "Material", 500, null, isMaterial = true),
            ObjectReportRow(3, 30, 1, "Worker", "Future", 9999, null)
        )
        val payments = listOf(CustomerPayment(objectId=1,date=10,amount=2000), CustomerPayment(objectId=1,date=20,amount=1000), CustomerPayment(objectId=1,date=30,amount=9999))
        assertEquals(ReportPeriod(3000, 500, 1000, -1000, 1500, false), summarizePeriod(rows, payments, 20, 20))
        assertEquals(-1000L, summarizePeriod(rows, payments, 11, 19).closing)
    }

    @Test fun `missing amount before period keeps balance provisional but future missing value does not`() {
        val old = ObjectReportRow(1, 10, 1, "Worker", "Work", 0, null, isAmountPending = true)
        assertTrue(summarizePeriod(listOf(old), emptyList(), 20, 25).pending)
        assertFalse(summarizePeriod(listOf(old.copy(date=30)), emptyList(), 20, 25).pending)
        try { summarizePeriod(emptyList(), emptyList(), 20, 10); fail() } catch (_: IllegalArgumentException) { }
    }
}
