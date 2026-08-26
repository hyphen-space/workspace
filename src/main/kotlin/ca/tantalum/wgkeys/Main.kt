package ca.tantalum.wgkeys

import ca.tantalum.wgkeys.http.peerApi
import ca.tantalum.wgkeys.peer.application.PeerDirectory
import ca.tantalum.wgkeys.peer.infrastructure.SqlitePeerRepository
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlin.io.path.Path

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT
    val registrationToken =
        requireNotNull(System.getenv(REGISTRATION_TOKEN_ENV)) {
            "$REGISTRATION_TOKEN_ENV must be set"
        }

    val databasePath = Path(System.getenv(DATABASE_PATH_ENV) ?: DEFAULT_DATABASE_PATH)
    val directory = PeerDirectory(SqlitePeerRepository(databasePath))

    println("WireGuard peer directory listens on http://0.0.0.0:$port")
    embeddedServer(CIO, host = SERVER_HOST, port = port) {
        peerApi(directory, registrationToken)
    }.start(wait = true)
}

private const val DEFAULT_PORT = 8080
private const val REGISTRATION_TOKEN_ENV = "REGISTRATION_TOKEN"
private const val DATABASE_PATH_ENV = "DATABASE_PATH"
private const val DEFAULT_DATABASE_PATH = "data/wgkeys.db"
private const val SERVER_HOST = "0.0.0.0"
