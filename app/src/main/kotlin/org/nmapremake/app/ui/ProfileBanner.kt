package org.nmapremake.app.ui

import org.nmapremake.core.capability.Availability
import org.nmapremake.core.capability.Capability
import org.nmapremake.core.capability.CapabilityProfile

/**
 * One banner row: a capability, its availability on the executor, and the
 * basis text from docs/CAPABILITY-MATRIX.md. Rows are produced by the pure
 * function [profileToBanner] — the UI never hardcodes its own capability
 * strings (ARCHITECTURE §6).
 */
data class CapabilityRow(
    val capability: Capability,
    val availability: Availability,
    val title: String,
    val rationale: String,
)

fun profileToBanner(profile: CapabilityProfile): List<CapabilityRow> =
    Capability.entries.mapNotNull { capability ->
        val availability = profile.availability(capability)
        if (availability == Availability.UNKNOWN) {
            null
        } else {
            CapabilityRow(
                capability = capability,
                availability = availability,
                title = TITLES.getValue(capability),
                rationale = RATIONALES.getValue(capability),
            )
        }
    }

private val TITLES: Map<Capability, String> = mapOf(
    Capability.TCP_CONNECT to "TCP connect scan",
    Capability.UDP_APP_PROBES to "UDP probes",
    Capability.SERVICE_DETECTION to "Service detection",
    Capability.FINGERPRINT_INFERENCE to "Fingerprinting",
    Capability.SCRIPT_PIPELINE to "Script pipeline",
    Capability.HOST_DISCOVERY_CONNECT to "Host discovery (connect)",
    Capability.HOST_DISCOVERY_RAW to "Host discovery (raw)",
    Capability.RAW_PACKET_BUILD to "Packet building",
    Capability.RAW_PACKET_TRANSMIT to "Packet transmission",
    Capability.PACKET_CAPTURE to "Packet capture",
    Capability.OS_DETECTION_NATIVE to "OS detection",
    Capability.TRACEROUTE to "Traceroute",
    Capability.IDLE_SCAN to "Idle scan",
    Capability.FTP_BOUNCE to "FTP bounce",
    Capability.REMOTE_DELEGATION to "Remote delegation",
    Capability.RESULT_EXPORT_JSON to "JSON export",
)

private val RATIONALES: Map<Capability, String> = mapOf(
    Capability.TCP_CONNECT to "Unprivileged connect scan — available on this device.",
    Capability.UDP_APP_PROBES to "Planned: unprivileged UDP sockets (M3).",
    Capability.SERVICE_DETECTION to "Planned: app-layer service identification (M2).",
    Capability.FINGERPRINT_INFERENCE to "Planned: app-layer inference, clearly labeled (M4).",
    Capability.SCRIPT_PIPELINE to "Planned: typed Kotlin probe pipeline (M4).",
    Capability.HOST_DISCOVERY_CONNECT to "Planned: connect-based host discovery (M2).",
    Capability.HOST_DISCOVERY_RAW to "Not available: ARP/ICMP need raw sockets.",
    Capability.RAW_PACKET_BUILD to "Packet serialization is pure code — always available.",
    Capability.RAW_PACKET_TRANSMIT to "Not available: Android denies raw sends (EPERM).",
    Capability.PACKET_CAPTURE to "Not available: capture needs root/pcap.",
    Capability.OS_DETECTION_NATIVE to "Not available: needs raw transmission and capture.",
    Capability.TRACEROUTE to "Unverified on this device (M6 experiment planned).",
    Capability.IDLE_SCAN to "Not available: needs spoofing and capture.",
    Capability.FTP_BOUNCE to "Planned but deferred: deprecated and abusable.",
    Capability.REMOTE_DELEGATION to "Planned: authenticated delegation (M7).",
    Capability.RESULT_EXPORT_JSON to "Schema-versioned JSON reports — available.",
)
