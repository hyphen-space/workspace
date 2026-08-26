package ca.tantalum.wgkeys.peer.application

import ca.tantalum.wgkeys.peer.domain.AddResult
import ca.tantalum.wgkeys.peer.domain.Peer
import ca.tantalum.wgkeys.peer.domain.PeerRepository
import ca.tantalum.wgkeys.peer.domain.UpdateResult
import java.security.MessageDigest
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

class PeerDirectory(
    private val repository: PeerRepository,
    private val clock: Clock = Clock.System,
) {
    fun register(
        name: String,
        address: String,
        publicKey: String,
        endpoint: String,
    ): Registration {
        val peer = Peer(name, address, publicKey, endpoint, clock.now())
        val token = UUID.randomUUID().toString() + UUID.randomUUID().toString()

        if (repository.add(peer, hash(token)) == AddResult.CONFLICT) {
            throw PeerConflict(PEER_CONFLICT_MESSAGE)
        }

        return Registration(peer.toView(), token)
    }

    fun list(): List<PeerView> = repository.all().map(Peer::toView)

    fun find(name: String): PeerView = findPeer(name).toView()

    fun config(name: String): String {
        val owner = findPeer(name)
        val otherPeers = repository.all().filterNot { it.name == name }

        return buildString {
            appendLine("# Generated for ${owner.name}")
            appendLine("# Replace the private-key placeholder before use.")
            appendLine("[Interface]")
            appendLine("PrivateKey = $PRIVATE_KEY_PLACEHOLDER")
            appendLine("Address = ${owner.address}")

            otherPeers.forEach { peer ->
                appendLine()
                appendLine("# ${peer.name}")
                appendLine("[Peer]")
                appendLine("PublicKey = ${peer.publicKey}")
                appendLine("AllowedIPs = ${peer.address}")
                appendLine("Endpoint = ${peer.endpoint}")
            }
        }
    }

    fun update(
        name: String,
        address: String,
        publicKey: String,
        endpoint: String,
        token: String,
    ): PeerView {
        authorize(name, token)
        val peer = Peer(name, address, publicKey, endpoint, clock.now())

        when (repository.update(peer)) {
            UpdateResult.UPDATED -> return peer.toView()
            UpdateResult.MISSING -> throw PeerMissing("Peer '$name' does not exist")
            UpdateResult.CONFLICT -> throw PeerConflict(PEER_CONFLICT_MESSAGE)
        }
    }

    fun delete(
        name: String,
        token: String,
    ) {
        authorize(name, token)

        if (!repository.delete(name)) {
            throw PeerMissing("Peer '$name' does not exist")
        }
    }

    private fun authorize(
        name: String,
        token: String,
    ) {
        val stored = repository.tokenHash(name) ?: throw PeerMissing("Peer '$name' does not exist")
        if (!MessageDigest.isEqual(stored.toByteArray(), hash(token).toByteArray())) {
            throw PeerUnauthorized("The bearer token is not valid")
        }
    }

    private fun findPeer(name: String): Peer = repository.find(name) ?: throw PeerMissing("Peer '$name' does not exist")

    private fun hash(token: String): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM).digest(token.toByteArray())
        return Base64.encode(digest)
    }

    private companion object {
        const val HASH_ALGORITHM = "SHA-256"
        const val PRIVATE_KEY_PLACEHOLDER = "REPLACE_WITH_PRIVATE_KEY"
        const val PEER_CONFLICT_MESSAGE = "Peer name, address, and public key must be unique"
    }
}

data class PeerView(
    val name: String,
    val address: String,
    val publicKey: String,
    val endpoint: String,
    val updatedAt: Instant,
)

data class Registration(val peer: PeerView, val token: String)

private fun Peer.toView(): PeerView = PeerView(name, address, publicKey, endpoint, updatedAt)

class PeerConflict(message: String) : RuntimeException(message)

class PeerMissing(message: String) : RuntimeException(message)

class PeerUnauthorized(message: String) : RuntimeException(message)
