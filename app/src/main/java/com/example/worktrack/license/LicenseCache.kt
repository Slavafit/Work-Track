package com.example.worktrack.license

internal fun shouldUseLicenseCache(forceRefresh: Boolean, checkedAt: Long, now: Long): Boolean {
    if (forceRefresh || checkedAt <= 0 || now < checkedAt) return false
    return now - checkedAt < 24 * 60 * 60 * 1000L
}
