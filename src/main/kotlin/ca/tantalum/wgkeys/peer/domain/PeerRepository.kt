package ca.tantalum.wgkeys.peer.domain

interface PeerRepository {
    fun add(
        peer: Peer,
        tokenHash: String,
    ): AddResult

    fun all(): List<Peer>

    fun find(name: String): Peer?

    fun tokenHash(name: String): String?

    fun update(peer: Peer): UpdateResult

    fun delete(name: String): Boolean
}

enum class AddResult {
    ADDED,
    CONFLICT,
}

enum class UpdateResult {
    UPDATED,
    MISSING,
    CONFLICT,
}
