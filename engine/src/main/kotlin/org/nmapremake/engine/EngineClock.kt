package org.nmapremake.engine

/** Injectable wall clock so timing-dependent code stays testable. */
fun interface EngineClock {
    fun now(): Long
}

object SystemClock : EngineClock {
    override fun now(): Long = System.currentTimeMillis()
}
