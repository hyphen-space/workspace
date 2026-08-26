package ca.tantalum.wgkeys.peer.infrastructure

import ca.tantalum.wgkeys.peer.domain.AddResult
import ca.tantalum.wgkeys.peer.domain.Peer
import ca.tantalum.wgkeys.peer.domain.PeerRepository
import ca.tantalum.wgkeys.peer.domain.UpdateResult
import java.util.concurrent.ConcurrentHashMap

class InMemoryPeerRepository : PeerRepository {
    private data class Entry(val peer: Peer, val tokenHash: String)

    private val peers = ConcurrentHashMap<String, Entry>()
    private val writeLock = Any()

    override fun add(
        peer: Peer,
        tokenHash: String,
    ): AddResult =
        // Keep the uniqueness check and write in one critical section.
        synchronized(writeLock) {
            if (peers.containsKey(peer.name) || conflicts(peer)) {
                return@synchronized AddResult.CONFLICT
            }

            peers[peer.name] = Entry(peer, tokenHash)
            AddResult.ADDED
        }

    override fun all(): List<Peer> = peers.values.map { it.peer }.sortedBy { it.name }

    override fun find(name: String): Peer? = peers[name]?.peer

    override fun tokenHash(name: String): String? = peers[name]?.tokenHash

    override fun update(peer: Peer): UpdateResult =
        synchronized(writeLock) {
            val entry = peers[peer.name] ?: return@synchronized UpdateResult.MISSING
            if (conflicts(peer)) {
                return@synchronized UpdateResult.CONFLICT
            }

            peers[peer.name] = entry.copy(peer = peer)
            UpdateResult.UPDATED
        }

    override fun delete(name: String): Boolean = synchronized(writeLock) { peers.remove(name) != null }

    private fun conflicts(peer: Peer): Boolean =
        peers.values.any { entry ->
            entry.peer.name != peer.name &&
                (entry.peer.address == peer.address || entry.peer.publicKey == peer.publicKey)
        }
}
