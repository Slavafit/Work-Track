package com.example.worktrack

import com.example.worktrack.data.ObjectSummary
import org.junit.Assert.*
import org.junit.Test

class ObjectSearchTest {
    private val active = ObjectSummary(1, "Calle Málaga 12", "José Иванов", false, null, 15000, 2, "+34 (612) 345-678", 5000)
    private val closed = ObjectSummary(2, "Лесная 7", "Пётр", true, 1000, 10000, 1, null, 2000)
    private val paid = active.copy(id = 3, paidAmount = 15000)
    private val advance = active.copy(id = 4, paidAmount = 20000)
    private val objects = listOf(active, closed, paid, advance)

    @Test fun `search matches names addresses and formatted phone numbers`() {
        for (query in listOf("MALAGA", "jose", "  Иванов   calle ", "+34 612345678", "(612) 345-678")) {
            assertEquals(query, listOf(active), filterObjects(listOf(active), query, "all", false))
        }
        assertEquals(listOf(closed), filterObjects(objects, "петр", "all", false))
        assertTrue(filterObjects(objects, "nonexistent", "all", false).isEmpty())
        assertEquals(objects, filterObjects(objects, "  ", "all", false))
    }

    @Test fun `status and balance filters intersect and exclude settled objects and advances`() {
        assertEquals(listOf(active, closed), filterObjects(objects, "", "all", true))
        assertEquals(listOf(closed), filterObjects(objects, "", "completed", true))
        assertEquals(listOf(active), filterObjects(objects, "jose", "active", true))
        assertTrue(filterObjects(objects, "jose", "completed", true).isEmpty())
        assertEquals(listOf(active, paid, advance), filterObjects(objects, "", "active", false))
    }
}
