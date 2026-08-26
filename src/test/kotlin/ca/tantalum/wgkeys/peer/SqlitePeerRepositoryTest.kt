package ca.tantalum.wgkeys.peer

import ca.tantalum.wgkeys.peer.domain.AddResult
import ca.tantalum.wgkeys.peer.domain.Peer
import ca.tantalum.wgkeys.peer.domain.UpdateResult
import ca.tantalum.wgkeys.peer.infrastructure.SqlitePeerRepository
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SqlitePeerRepositoryTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `persists peers across repository instances`() {
        val database = directory.resolve("peers.db")
        val peer =
            Peer(
                name = "alice",
                address = "10.0.0.1/32",
                publicKey = PUBLIC_KEY,
                endpoint = "alice.example.com:51820",
                updatedAt = UPDATED_AT,
            )

        assertEquals(AddResult.ADDED, SqlitePeerRepository(database).add(peer, TOKEN_HASH))
        val reopened = SqlitePeerRepository(database)

        assertEquals(peer, reopened.find("alice"))
        assertEquals(TOKEN_HASH, reopened.tokenHash("alice"))
    }

    @Test
    fun `rejects duplicate peer identities`() {
        val repository = SqlitePeerRepository(directory.resolve("peers.db"))
        val alice = peer("alice", "10.0.0.1/32", PUBLIC_KEY)
        val duplicateAddress = peer("bob", alice.address, SECOND_PUBLIC_KEY)
        val duplicateKey = peer("carol", "10.0.0.3/32", alice.publicKey)

        assertEquals(AddResult.ADDED, repository.add(alice, TOKEN_HASH))
        assertEquals(AddResult.CONFLICT, repository.add(duplicateAddress, TOKEN_HASH))
        assertEquals(AddResult.CONFLICT, repository.add(duplicateKey, TOKEN_HASH))
    }

    @Test
    fun `rejects a conflicting update`() {
        val repository = SqlitePeerRepository(directory.resolve("peers.db"))
        val alice = peer("alice", "10.0.0.1/32", PUBLIC_KEY)
        val bob = peer("bob", "10.0.0.2/32", SECOND_PUBLIC_KEY)
        repository.add(alice, TOKEN_HASH)
        repository.add(bob, TOKEN_HASH)

        assertEquals(UpdateResult.CONFLICT, repository.update(bob.copy(address = alice.address)))
        assertEquals(bob, repository.find("bob"))
    }

    private fun peer(
        name: String,
        address: String,
        publicKey: String,
    ): Peer =
        Peer(
            name = name,
            address = address,
            publicKey = publicKey,
            endpoint = "$name.example.com:51820",
            updatedAt = UPDATED_AT,
        )

    private companion object {
        const val PUBLIC_KEY = "TrMvSoP4jYQlY6RIzBgbssQqY3vxI2Pi+y71lOWWXX0="
        const val SECOND_PUBLIC_KEY = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBA="
        const val TOKEN_HASH = "stored-token-hash"
        val UPDATED_AT: Instant = Instant.parse("2026-08-26T00:00:00Z")
    }
}
