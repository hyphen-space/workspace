package ca.tantalum.wgkeys.peer

import ca.tantalum.wgkeys.peer.application.PeerConflict
import ca.tantalum.wgkeys.peer.application.PeerDirectory
import ca.tantalum.wgkeys.peer.application.PeerUnauthorized
import ca.tantalum.wgkeys.peer.infrastructure.InMemoryPeerRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Instant

class PeerDirectoryTest {
    private val clock =
        object : Clock {
            override fun now(): Instant = UPDATED_AT
        }
    private val directory = PeerDirectory(InMemoryPeerRepository(), clock)

    @Test
    fun `registers and updates a peer`() {
        val registration = directory.register("alice", "10.0.0.1/32", PUBLIC_KEY, ENDPOINT)

        val peer = directory.update("alice", "10.0.0.2/32", PUBLIC_KEY, NEW_ENDPOINT, registration.token)

        assertEquals("10.0.0.2/32", peer.address)
        assertEquals(NEW_ENDPOINT, peer.endpoint)
        assertEquals(peer, directory.find("alice"))
    }

    @Test
    fun `rejects a duplicate name`() {
        directory.register("alice", "10.0.0.1/32", PUBLIC_KEY, ENDPOINT)

        assertFailsWith<PeerConflict> {
            directory.register("alice", "10.0.0.2/32", PUBLIC_KEY, ENDPOINT)
        }
    }

    @Test
    fun `rejects a duplicate address`() {
        directory.register("alice", "10.0.0.1/32", PUBLIC_KEY, ENDPOINT)

        assertFailsWith<PeerConflict> {
            directory.register("bob", "10.0.0.1/32", SECOND_PUBLIC_KEY, NEW_ENDPOINT)
        }
    }

    @Test
    fun `rejects a duplicate public key`() {
        directory.register("alice", "10.0.0.1/32", PUBLIC_KEY, ENDPOINT)

        assertFailsWith<PeerConflict> {
            directory.register("bob", "10.0.0.2/32", PUBLIC_KEY, NEW_ENDPOINT)
        }
    }

    @Test
    fun `rejects a conflicting update`() {
        val alice = directory.register("alice", "10.0.0.1/32", PUBLIC_KEY, ENDPOINT)
        val bob = directory.register("bob", "10.0.0.2/32", SECOND_PUBLIC_KEY, NEW_ENDPOINT)

        assertFailsWith<PeerConflict> {
            directory.update("bob", "10.0.0.1/32", SECOND_PUBLIC_KEY, NEW_ENDPOINT, bob.token)
        }
        assertEquals("10.0.0.1/32", directory.find("alice").address)
        assertEquals(alice.peer, directory.find("alice"))
    }

    @Test
    fun `rejects an invalid token`() {
        directory.register("alice", "10.0.0.1/32", PUBLIC_KEY, ENDPOINT)

        assertFailsWith<PeerUnauthorized> {
            directory.update("alice", "10.0.0.2/32", PUBLIC_KEY, ENDPOINT, "wrong")
        }
    }

    @Test
    fun `rejects an invalid WireGuard key`() {
        assertFailsWith<IllegalArgumentException> {
            directory.register("alice", "10.0.0.1/32", "not-a-key", ENDPOINT)
        }
    }

    @Test
    fun `rejects a zero WireGuard key`() {
        assertFailsWith<IllegalArgumentException> {
            directory.register("alice", "10.0.0.1/32", ZERO_KEY, ENDPOINT)
        }
    }

    @Test
    fun `rejects non-canonical base64`() {
        assertFailsWith<IllegalArgumentException> {
            directory.register("alice", "10.0.0.1/32", NON_CANONICAL_KEY, ENDPOINT)
        }
    }

    @Test
    fun `rejects an invalid endpoint`() {
        assertFailsWith<IllegalArgumentException> {
            directory.register("alice", "10.0.0.1/32", PUBLIC_KEY, "https://vpn.example.com")
        }
    }

    private companion object {
        const val PUBLIC_KEY = "TrMvSoP4jYQlY6RIzBgbssQqY3vxI2Pi+y71lOWWXX0="
        const val SECOND_PUBLIC_KEY = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBA="
        const val ZERO_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        const val NON_CANONICAL_KEY = "TrMvSoP4jYQlY6RIzBgbssQqY3vxI2Pi+y71lOWWXX1="
        const val ENDPOINT = "vpn.example.com:51820"
        const val NEW_ENDPOINT = "203.0.113.10:51820"
        val UPDATED_AT: Instant = Instant.parse("2026-08-26T00:00:00Z")
    }
}
