package ca.tantalum.wgkeys.http

import ca.tantalum.wgkeys.peer.application.PeerConflict
import ca.tantalum.wgkeys.peer.application.PeerDirectory
import ca.tantalum.wgkeys.peer.application.PeerMissing
import ca.tantalum.wgkeys.peer.application.PeerUnauthorized
import ca.tantalum.wgkeys.peer.application.PeerView
import io.ktor.http.CacheControl
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.text.toHexString
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun Application.peerApi(
    directory: PeerDirectory,
    registrationToken: String,
    limits: RateLimits = RateLimits(),
) {
    require(registrationToken.length >= MIN_TOKEN_LENGTH) {
        "Registration token must contain at least $MIN_TOKEN_LENGTH characters"
    }
    val logger = environment.log

    install(ContentNegotiation) {
        json()
    }
    install(Authentication) {
        bearer(REGISTRATION_AUTH) {
            realm = AUTH_REALM
            authenticate { credential ->
                if (secureEquals(credential.token, registrationToken)) {
                    UserIdPrincipal("registrar")
                } else {
                    null
                }
            }
        }
        bearer(OWNER_AUTH) {
            realm = AUTH_REALM
            authenticate { credential -> OwnerPrincipal(credential.token) }
        }
    }
    install(RateLimit) {
        global {
            requestKey { call -> call.request.origin.remoteHost }
            rateLimiter(limit = limits.requestsPerMinute, refillPeriod = 1.minutes)
        }
        register(REGISTRATION_RATE_LIMIT) {
            requestKey { call -> call.request.origin.remoteHost }
            rateLimiter(limit = limits.registrationsPerHour, refillPeriod = 1.hours)
        }
    }
    install(StatusPages) {
        exception<PeerConflict> { call, cause -> call.error(HttpStatusCode.Conflict, cause) }
        exception<PeerMissing> { call, cause -> call.error(HttpStatusCode.NotFound, cause) }
        exception<PeerUnauthorized> { call, cause ->
            call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
            call.error(HttpStatusCode.Unauthorized, cause)
        }
        exception<PayloadTooLargeException> { call, _ ->
            call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("Request body is too large"))
        }
        exception<ContentTransformationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Request body must be valid JSON"))
        }
        exception<IllegalArgumentException> { call, cause -> call.error(HttpStatusCode.BadRequest, cause) }
        // Cancellation must reach Ktor so disconnected requests stop work.
        exception<CancellationException> { _, cause -> throw cause }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled request failure", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
        }
        status(HttpStatusCode.NotFound) { call, status -> call.respond(status, ErrorResponse("Route not found")) }
        status(HttpStatusCode.TooManyRequests) { call, status -> call.respond(status, ErrorResponse("Too many requests")) }
    }

    routing {
        install(RequestBodyLimit) {
            bodyLimit { MAX_BODY_BYTES }
        }

        get("/") {
            call.respondPage()
        }
        get(STYLE_PATH) {
            call.respondAsset(STYLE_RESOURCE, ContentType.Text.CSS)
        }
        get(SCRIPT_PATH) {
            call.respondAsset(SCRIPT_RESOURCE, ContentType.Text.JavaScript)
        }
        route(API_PATH) {
            get {
                val peers = directory.list().map(PeerView::toResponse)
                val tag = '"' + sha256(peers.toString()).take(ETAG_LENGTH) + '"'
                call.response.header(HttpHeaders.ETag, tag)
                call.response.header(HttpHeaders.CacheControl, "public, max-age=$CACHE_SECONDS")

                if (call.request.headers[HttpHeaders.IfNoneMatch] == tag) {
                    call.respond(HttpStatusCode.NotModified)
                    return@get
                }

                call.respond(peers)
            }
            rateLimit(REGISTRATION_RATE_LIMIT) {
                authenticate(REGISTRATION_AUTH) {
                    post {
                        val input = call.receive<RegisterPeerRequest>()
                        val registration =
                            directory.register(input.name, input.address, input.publicKey, input.endpoint)
                        call.response.header(HttpHeaders.CacheControl, CacheControl.NoStore(null).toString())
                        call.respond(HttpStatusCode.Created, RegistrationResponse(registration.peer.toResponse(), registration.token))
                    }
                }
            }
            route("/{name}") {
                get {
                    call.respond(directory.find(call.peerName()).toResponse())
                }
                get("/wg.conf") {
                    val name = call.peerName()
                    val disposition =
                        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "$name-wg.conf")
                    call.response.header(HttpHeaders.ContentDisposition, disposition.toString())
                    call.respondText(directory.config(name), ContentType.Text.Plain)
                }
                authenticate(OWNER_AUTH) {
                    put {
                        val input = call.receive<UpdatePeerRequest>()
                        val peer =
                            directory.update(
                                call.peerName(),
                                input.address,
                                input.publicKey,
                                input.endpoint,
                                call.ownerToken(),
                            )
                        call.respond(peer.toResponse())
                    }
                    delete {
                        directory.delete(call.peerName(), call.ownerToken())
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }
}

data class RateLimits(
    val requestsPerMinute: Int = REQUEST_LIMIT,
    val registrationsPerHour: Int = REGISTRATION_LIMIT,
) {
    init {
        require(requestsPerMinute > 0) { "Request limit must be positive" }
        require(registrationsPerHour > 0) { "Registration limit must be positive" }
    }
}

private data class OwnerPrincipal(val token: String)

@Serializable
private data class RegisterPeerRequest(
    val name: String,
    val address: String,
    val publicKey: String,
    val endpoint: String,
)

@Serializable
private data class UpdatePeerRequest(
    val address: String,
    val publicKey: String,
    val endpoint: String,
)

@Serializable
private data class PeerResponse(
    val name: String,
    val address: String,
    val publicKey: String,
    val endpoint: String,
    val updatedAt: String,
)

@Serializable
private data class RegistrationResponse(val peer: PeerResponse, val token: String)

@Serializable
private data class ErrorResponse(val error: String)

private fun PeerView.toResponse(): PeerResponse = PeerResponse(name, address, publicKey, endpoint, updatedAt.toString())

private fun ApplicationCall.peerName(): String = parameters["name"]?.takeIf { it.isNotBlank() } ?: throw PeerMissing("Peer name is missing")

private fun ApplicationCall.ownerToken(): String = checkNotNull(principal<OwnerPrincipal>()) { "Owner token is missing" }.token

private suspend fun ApplicationCall.respondPage() {
    response.header(CONTENT_SECURITY_POLICY_HEADER, CONTENT_SECURITY_POLICY)
    response.header(X_CONTENT_TYPE_OPTIONS_HEADER, "nosniff")
    response.header(REFERRER_POLICY_HEADER, "no-referrer")
    respondText(resourceText(INDEX_RESOURCE), ContentType.Text.Html)
}

private suspend fun ApplicationCall.respondAsset(
    name: String,
    type: ContentType,
) {
    response.header(X_CONTENT_TYPE_OPTIONS_HEADER, "nosniff")
    respondText(resourceText(name), type)
}

private suspend fun ApplicationCall.error(
    status: HttpStatusCode,
    cause: Throwable,
) {
    respond(status, ErrorResponse(cause.message.orEmpty()))
}

private fun secureEquals(
    first: String,
    second: String,
): Boolean = MessageDigest.isEqual(first.toByteArray(), second.toByteArray())

private fun sha256(value: String): String = MessageDigest.getInstance(HASH_ALGORITHM).digest(value.toByteArray()).toHexString()

private fun resourceText(name: String): String =
    checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream(name)) { "Resource '$name' is missing" }
        .bufferedReader()
        .use { it.readText() }

private const val API_PATH = "/api/peers"
private const val REGISTRATION_AUTH = "registration"
private const val OWNER_AUTH = "owner"
private const val AUTH_REALM = "WireGuard peer directory"
private const val INDEX_RESOURCE = "index.html"
private const val STYLE_RESOURCE = "index.css"
private const val SCRIPT_RESOURCE = "index.js"
private const val STYLE_PATH = "/assets/index.css"
private const val SCRIPT_PATH = "/assets/index.js"
private const val HASH_ALGORITHM = "SHA-256"
private const val CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy"
private const val X_CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
private const val REFERRER_POLICY_HEADER = "Referrer-Policy"
private const val CONTENT_SECURITY_POLICY =
    "default-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; object-src 'none'"
private const val CACHE_SECONDS = 30
private const val ETAG_LENGTH = 16
private const val REQUEST_LIMIT = 120
private const val REGISTRATION_LIMIT = 10
private const val MAX_BODY_BYTES = 16_384L
private const val MIN_TOKEN_LENGTH = 32
private val REGISTRATION_RATE_LIMIT = RateLimitName("registration")
