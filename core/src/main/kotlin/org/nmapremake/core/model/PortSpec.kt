package org.nmapremake.core.model

import kotlinx.serialization.Serializable

/**
 * A specification of TCP ports to scan. Grammar (M1): single port, comma
 * list, inclusive range, `top-100` (self-authored curated list), `all`
 * (1..65535). Parsed atomically by [org.nmapremake.core.parse.PortSpecParser]:
 * one invalid token rejects the entire spec. Parser-produced [List] specs are
 * deduped with order kept.
 */
@Serializable
sealed interface PortSpec {
    @Serializable
    data class Single(val port: Int) : PortSpec

    @Serializable
    data class List(val ports: List<Int>) : PortSpec

    @Serializable
    data class Range(val first: Int, val last: Int) : PortSpec

    @Serializable
    data object TopPorts : PortSpec {
        /**
         * Self-authored curated list of 100 commonly relevant TCP ports.
         *
         * Clean-room note: authored for this project from general knowledge
         * of well-known service ports (IANA registry), NOT copied from Nmap's
         * NPSL-licensed `nmap-services` open-frequency data. See
         * docs/NMAP-DEEP-DIVE.md.
         */
        val curated: List<Int> = CURATED_TOP_PORTS
    }

    @Serializable
    data object All : PortSpec

    /** Expands the spec into the concrete port list. Port range is 1..65535. */
    fun expand(): List<Int> = when (this) {
        is Single -> listOf(port)
        is List -> ports
        is Range -> (first..last).toList()
        TopPorts -> TopPorts.curated
        All -> (1..65535).toList()
    }

    companion object {
        const val MIN_PORT = 1
        const val MAX_PORT = 65535
    }
}

private val CURATED_TOP_PORTS: List<Int> = listOf(
    80, 443, 22, 21, 25, 53, 110, 143, 993, 995,
    8080, 8443, 23, 587, 465, 3306, 5432, 6379, 27017, 3389,
    5900, 139, 445, 135, 137, 138, 161, 162, 389, 636,
    1433, 1434, 1521, 33060, 9000, 9090, 9200, 9300, 11211, 5672,
    15672, 1883, 8883, 8081, 8000, 8888, 5000, 5001, 7001,
    7002, 8008, 8009, 8010, 8042, 8069, 8161, 8181, 8222, 8230,
    8291, 8333, 9001, 9002, 9003, 9080, 9091, 9160, 9306, 9418,
    9530, 10000, 10001, 10080, 10443, 11210, 11215, 12345, 16000, 16379,
    18080, 20000, 27015, 28017, 30000, 31415, 32768, 40000, 44818, 47808,
    50000, 50030, 50060, 50070, 50075, 50090, 54321, 55555, 61616, 65535,
)
