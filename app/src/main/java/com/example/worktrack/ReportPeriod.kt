package com.example.worktrack

import com.example.worktrack.data.CustomerPayment
import com.example.worktrack.data.ObjectReportRow

data class ReportPeriod(val work: Long, val materials: Long, val paid: Long, val opening: Long, val closing: Long, val pending: Boolean)

fun summarizePeriod(rows: List<ObjectReportRow>, payments: List<CustomerPayment>, start: Long, end: Long): ReportPeriod {
    require(start <= end)
    fun total(values: List<Long>) = requireNotNull(checkedAmountTotal(values))
    val selected = rows.filter { it.date in start..end }
    return ReportPeriod(
        total(selected.filterNot { it.isMaterial }.map { it.amount }),
        total(selected.filter { it.isMaterial }.map { it.amount }),
        total(payments.filter { it.date in start..end }.map { it.amount }),
        total(rows.filter { it.date < start }.map { it.amount }) - total(payments.filter { it.date < start }.map { it.amount }),
        total(rows.filter { it.date <= end }.map { it.amount }) - total(payments.filter { it.date <= end }.map { it.amount }),
        rows.any { it.date <= end && it.isAmountPending }
    )
}
