package org.nmapremake.core.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortSpecTest {

    @Test
    fun `single expands to one port`() {
        assertEquals(listOf(80), PortSpec.Single(80).expand())
    }

    @Test
    fun `list expands to itself`() {
        assertEquals(listOf(80, 443), PortSpec.List(listOf(80, 443)).expand())
    }

    @Test
    fun `range expands inclusively`() {
        assertEquals(listOf(1, 2, 3), PortSpec.Range(1, 3).expand())
    }

    @Test
    fun `range of one expands to one`() {
        assertEquals(listOf(5), PortSpec.Range(5, 5).expand())
    }

    @Test
    fun `all expands to the full port space`() {
        val all = PortSpec.All.expand()
        assertEquals(65535, all.size)
        assertEquals(1, all.first())
        assertEquals(65535, all.last())
    }

    @Test
    fun `curated top list has exactly 100 distinct in-range ports`() {
        val curated = PortSpec.TopPorts.curated
        assertEquals(100, curated.size)
        assertEquals(100, curated.toSet().size)
        assertTrue(curated.all { it in 1..65535 })
    }

    @Test
    fun `curated top list is sorted and contains core well-known ports`() {
        val curated = PortSpec.TopPorts.curated
        assertEquals(curated.sorted(), curated)
        for (port in listOf(22, 80, 443, 8080, 8443)) {
            assertTrue(port in curated, "expected port $port in curated list")
        }
    }
}
