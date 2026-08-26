package ca.tantalum.wgkeys.peer.infrastructure

import ca.tantalum.wgkeys.peer.domain.AddResult
import ca.tantalum.wgkeys.peer.domain.Peer
import ca.tantalum.wgkeys.peer.domain.PeerRepository
import ca.tantalum.wgkeys.peer.domain.UpdateResult
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import kotlin.io.path.createDirectories
import kotlin.time.Instant

class SqlitePeerRepository(databasePath: Path) : PeerRepository {
    private val jdbcUrl: String

    init {
        val absolutePath = databasePath.toAbsolutePath()
        absolutePath.parent.createDirectories()
        jdbcUrl = "jdbc:sqlite:$absolutePath"

        connect { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode = WAL")
                statement.execute(CREATE_TABLE)
                statement.execute(CREATE_ADDRESS_INDEX)
                statement.execute(CREATE_KEY_INDEX)
            }
        }
    }

    override fun add(
        peer: Peer,
        tokenHash: String,
    ): AddResult =
        try {
            connect { connection ->
                connection.prepareStatement(INSERT).use { statement ->
                    statement.setString(1, peer.name)
                    statement.setString(2, peer.address)
                    statement.setString(3, peer.publicKey)
                    statement.setString(4, peer.endpoint)
                    statement.setString(5, peer.updatedAt.toString())
                    statement.setString(6, tokenHash)
                    statement.executeUpdate()
                    AddResult.ADDED
                }
            }
        } catch (exception: SQLException) {
            if (exception.errorCode == SQLITE_CONSTRAINT) {
                AddResult.CONFLICT
            } else {
                throw exception
            }
        }

    override fun all(): List<Peer> =
        connect { connection ->
            connection.prepareStatement(SELECT_ALL).use { statement ->
                statement.executeQuery().use { results ->
                    buildList {
                        while (results.next()) {
                            add(results.toPeer())
                        }
                    }
                }
            }
        }

    override fun find(name: String): Peer? =
        connect { connection ->
            connection.prepareStatement(SELECT_ONE).use { statement ->
                statement.setString(1, name)
                statement.executeQuery().use { results ->
                    if (results.next()) {
                        results.toPeer()
                    } else {
                        null
                    }
                }
            }
        }

    override fun tokenHash(name: String): String? =
        connect { connection ->
            connection.prepareStatement(SELECT_TOKEN).use { statement ->
                statement.setString(1, name)
                statement.executeQuery().use { results ->
                    if (results.next()) {
                        results.getString("token_hash")
                    } else {
                        null
                    }
                }
            }
        }

    override fun update(peer: Peer): UpdateResult =
        try {
            connect { connection ->
                connection.prepareStatement(UPDATE).use { statement ->
                    statement.setString(1, peer.address)
                    statement.setString(2, peer.publicKey)
                    statement.setString(3, peer.endpoint)
                    statement.setString(4, peer.updatedAt.toString())
                    statement.setString(5, peer.name)

                    if (statement.executeUpdate() == ROW_CHANGED) {
                        UpdateResult.UPDATED
                    } else {
                        UpdateResult.MISSING
                    }
                }
            }
        } catch (exception: SQLException) {
            if (exception.errorCode == SQLITE_CONSTRAINT) {
                UpdateResult.CONFLICT
            } else {
                throw exception
            }
        }

    override fun delete(name: String): Boolean =
        connect { connection ->
            connection.prepareStatement(DELETE).use { statement ->
                statement.setString(1, name)
                statement.executeUpdate() == ROW_CHANGED
            }
        }

    private fun <T> connect(action: (Connection) -> T): T =
        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA busy_timeout = $BUSY_TIMEOUT_MILLIS")
            }
            action(connection)
        }

    private fun ResultSet.toPeer(): Peer =
        Peer(
            name = getString("name"),
            address = getString("address"),
            publicKey = getString("public_key"),
            endpoint = getString("endpoint"),
            updatedAt = Instant.parse(getString("updated_at")),
        )

    private companion object {
        const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS peers (
                name TEXT PRIMARY KEY,
                address TEXT NOT NULL,
                public_key TEXT NOT NULL,
                endpoint TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                token_hash TEXT NOT NULL
            )
        """
        const val CREATE_ADDRESS_INDEX = "CREATE UNIQUE INDEX IF NOT EXISTS peers_address_unique ON peers (address)"
        const val CREATE_KEY_INDEX = "CREATE UNIQUE INDEX IF NOT EXISTS peers_public_key_unique ON peers (public_key)"
        const val INSERT = """
            INSERT INTO peers (name, address, public_key, endpoint, updated_at, token_hash)
            VALUES (?, ?, ?, ?, ?, ?)
        """
        const val SELECT_ALL = """
            SELECT name, address, public_key, endpoint, updated_at
            FROM peers
            ORDER BY name
        """
        const val SELECT_ONE = """
            SELECT name, address, public_key, endpoint, updated_at
            FROM peers
            WHERE name = ?
        """
        const val SELECT_TOKEN = "SELECT token_hash FROM peers WHERE name = ?"
        const val UPDATE = """
            UPDATE peers
            SET address = ?, public_key = ?, endpoint = ?, updated_at = ?
            WHERE name = ?
        """
        const val DELETE = "DELETE FROM peers WHERE name = ?"
        const val BUSY_TIMEOUT_MILLIS = 5_000
        const val ROW_CHANGED = 1
        const val SQLITE_CONSTRAINT = 19
    }
}
