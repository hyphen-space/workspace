package ca.tantalum.wgkeys.peer.domain

import kotlin.io.encoding.Base64
import kotlin.time.Instant

data class Peer(
    val name: String,
    val address: String,
    val publicKey: String,
    val endpoint: String,
    val updatedAt: Instant,
) {
    init {
        require(NAME.matches(name)) { "Name must contain 1-63 letters, numbers, dots, dashes, or underscores" }
        require(isCidr(address)) { "Address must be an IPv4 or IPv6 CIDR" }
        require(isWireGuardKey(publicKey)) { "Public key must be a canonical, non-zero WireGuard public key" }
        require(isEndpoint(endpoint)) { "Endpoint must use host:port or [IPv6]:port notation" }
    }

    private companion object {
        val NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,62}")
        val IPV4 = Regex("([0-9]{1,3}\\.){3}[0-9]{1,3}")
        val IPV6 = Regex("[0-9A-Fa-f:]+")
        val HOST_ENDPOINT = Regex("([^:]+):([0-9]{1,5})")
        val IPV6_ENDPOINT = Regex("\\[([0-9A-Fa-f:]+)]:([0-9]{1,5})")
        val HOST_LABEL = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")

        fun isCidr(value: String): Boolean {
            val parts = value.split('/')
            if (parts.size != CIDR_PARTS) {
                return false
            }

            val prefix = parts[1].toIntOrNull() ?: return false
            return isIpv4(parts[0], prefix) || isIpv6(parts[0], prefix)
        }

        fun isIpv4(
            address: String,
            prefix: Int,
        ): Boolean {
            if (prefix !in IPV4_PREFIX || !IPV4.matches(address)) {
                return false
            }

            return address.split('.').all { it.toIntOrNull() in IPV4_OCTET }
        }

        fun isIpv6(
            address: String,
            prefix: Int,
        ): Boolean {
            if (prefix !in IPV6_PREFIX || !IPV6.matches(address) || ':' !in address) {
                return false
            }

            return runCatching { java.net.InetAddress.getByName(address).address.size == IPV6_BYTES }.getOrDefault(false)
        }

        fun isWireGuardKey(value: String): Boolean {
            if (value.length != KEY_BASE64_LENGTH || !value.endsWith(KEY_PADDING)) {
                return false
            }

            val decoded = runCatching { Base64.decode(value) }.getOrNull() ?: return false
            if (decoded.size != KEY_BYTES || decoded.all { it == ZERO_BYTE }) {
                return false
            }

            return Base64.encode(decoded) == value
        }

        fun isEndpoint(value: String): Boolean {
            val ipv6 = IPV6_ENDPOINT.matchEntire(value)
            if (ipv6 != null) {
                return isIpv6(ipv6.groupValues[1], IPV6_PREFIX.last) && isPort(ipv6.groupValues[2])
            }

            val host = HOST_ENDPOINT.matchEntire(value) ?: return false
            return isHost(host.groupValues[1]) && isPort(host.groupValues[2])
        }

        fun isHost(value: String): Boolean {
            if (value.length > MAX_HOST_LENGTH || value.startsWith('.') || value.endsWith('.')) {
                return false
            }

            if (IPV4.matches(value)) {
                return isIpv4(value, IPV4_PREFIX.last)
            }

            return value.split('.').all(HOST_LABEL::matches)
        }

        fun isPort(value: String): Boolean = value.toIntOrNull() in PORT_RANGE

        const val CIDR_PARTS = 2
        const val IPV6_BYTES = 16
        const val KEY_BYTES = 32
        const val KEY_BASE64_LENGTH = 44
        const val KEY_PADDING = "="
        const val MAX_HOST_LENGTH = 253
        const val ZERO_BYTE: Byte = 0
        val IPV4_OCTET = 0..255
        val IPV4_PREFIX = 0..32
        val IPV6_PREFIX = 0..128
        val PORT_RANGE = 1..65_535
    }
}
