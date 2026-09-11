package org.nmapremake.core.model

import kotlinx.serialization.Serializable

/**
 * A scan target as specified by the user.
 *
 * [label] is the original user input; [resolved] is the resolved IP literal
 * (null until resolution happens at scan start — see PLAN section 5.1.1);
 * [family] is null for hostnames that have not been resolved yet.
 */
@Serializable
data class Target(
    val label: String,
    val resolved: String? = null,
    val family: IpFamily? = null,
) {
    /** The address to connect to: resolved literal, or the label when resolution is unnecessary. */
    fun effectiveAddress(): String = resolved ?: label
}

@Serializable
enum class IpFamily { IPV4, IPV6 }
