package ca.tantalum.wgkeys.http

import ca.tantalum.wgkeys.peer.application.PeerDirectory
import ca.tantalum.wgkeys.peer.infrastructure.InMemoryPeerRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ApiServerTest {
    @Test
    fun `serves the peer lifecycle`() =
        testApplication {
            application { peerApi(PeerDirectory(InMemoryPeerRepository()), REGISTRATION_TOKEN) }

            val created = client.send(HttpMethod.Post, "/api/peers", PEER_BODY, REGISTRATION_TOKEN)
            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals("no-store", created.headers[HttpHeaders.CacheControl])
            val token = created.bodyAsText().substringAfter("\"token\":\"").substringBefore('"')

            val listed = client.send(HttpMethod.Get, "/api/peers")
            assertEquals(HttpStatusCode.OK, listed.status)
            assertContains(listed.bodyAsText(), "\"name\":\"alice\"")
            assertContains(listed.bodyAsText(), "\"endpoint\":\"alice.example.com:51820\"")
            assertNotNull(listed.headers[HttpHeaders.ETag])

            val updated = client.send(HttpMethod.Put, "/api/peers/alice", UPDATE_BODY, token)
            assertEquals(HttpStatusCode.OK, updated.status)
            assertContains(updated.bodyAsText(), "10.0.0.2/32")

            val deleted = client.send(HttpMethod.Delete, "/api/peers/alice", token = token)
            assertEquals(HttpStatusCode.NoContent, deleted.status)
            assertEquals("[]", client.send(HttpMethod.Get, "/api/peers").bodyAsText())
        }

    @Test
    fun `requires the owner token`() =
        testApplication {
            application { peerApi(PeerDirectory(InMemoryPeerRepository()), REGISTRATION_TOKEN) }
            client.send(HttpMethod.Post, "/api/peers", PEER_BODY, REGISTRATION_TOKEN)

            assertEquals(HttpStatusCode.Unauthorized, client.send(HttpMethod.Put, "/api/peers/alice", UPDATE_BODY).status)
        }

    @Test
    fun `rejects a duplicate identity`() =
        testApplication {
            application { peerApi(PeerDirectory(InMemoryPeerRepository()), REGISTRATION_TOKEN) }
            client.send(HttpMethod.Post, "/api/peers", PEER_BODY, REGISTRATION_TOKEN)

            val response = client.send(HttpMethod.Post, "/api/peers", DUPLICATE_ADDRESS_BODY, REGISTRATION_TOKEN)

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertContains(response.bodyAsText(), "name, address, and public key must be unique")
        }

    @Test
    fun `serves the browser page`() =
        testApplication {
            application { peerApi(PeerDirectory(InMemoryPeerRepository()), REGISTRATION_TOKEN) }

            val response = client.send(HttpMethod.Get, "/")

            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "Public WireGuard key directory")
            assertEquals(CONTENT_SECURITY_POLICY, response.headers[CONTENT_SECURITY_POLICY_HEADER])
            assertEquals("nosniff", response.headers[X_CONTENT_TYPE_OPTIONS_HEADER])
            assertEquals("no-referrer", response.headers[REFERRER_POLICY_HEADER])

            val style = client.send(HttpMethod.Get, "/assets/index.css")
            val script = client.send(HttpMethod.Get, "/assets/index.js")
            assertEquals(HttpStatusCode.OK, style.status)
            assertContains(style.headers[HttpHeaders.ContentType].orEmpty(), "text/css")
            assertEquals(HttpStatusCode.OK, script.status)
            assertContains(script.headers[HttpHeaders.ContentType].orEmpty(), "javascript")
        }

    @Test
    fun `rejects a weak registration token`() {
        assertFailsWith<IllegalArgumentException> {
            testApplication {
                application { peerApi(PeerDirectory(InMemoryPeerRepository()), "short") }
                client.send(HttpMethod.Get, "/")
            }
        }
    }

    @Test
    fun `rejects non-positive limits`() {
        assertFailsWith<IllegalArgumentException> { RateLimits(requestsPerMinute = 0) }
        assertFailsWith<IllegalArgumentException> { RateLimits(registrationsPerHour = 0) }
    }

    @Test
    fun `downloads a WireGuard config`() =
        testApplication {
            application { peerApi(PeerDirectory(InMemoryPeerRepository()), REGISTRATION_TOKEN) }
            client.send(HttpMethod.Post, "/api/peers", PEER_BODY, REGISTRATION_TOKEN)
            client.send(HttpMethod.Post, "/api/peers", BOB_BODY, REGISTRATION_TOKEN)

            val response = client.send(HttpMethod.Get, "/api/peers/alice/wg.conf")

            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.headers[HttpHeaders.ContentDisposition].orEmpty(), "alice-wg.conf")
            assertContains(response.bodyAsText(), "[Interface]")
            assertContains(response.bodyAsText(), "Address = 10.0.0.1/32")
            assertContains(response.bodyAsText(), "PrivateKey = REPLACE_WITH_PRIVATE_KEY")
            assertContains(response.bodyAsText(), "# bob")
            assertContains(response.bodyAsText(), "AllowedIPs = 10.0.0.3/32")
            assertContains(response.bodyAsText(), "Endpoint = bob.example.com:51820")
        }

    @Test
    fun `requires the registration token`() =
        testApplication {
            application { peerApi(PeerDirectory(InMemoryPeerRepository()), REGISTRATION_TOKEN) }

            val response = client.send(HttpMethod.Post, "/api/peers", PEER_BODY)

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertContains(response.headers[HttpHeaders.WWWAuthenticate].orEmpty(), "Bearer")
        }

    @Test
    fun `rejects an invalid public key`() =
        testApplication {
            application { peerApi(PeerDirectory(InMemoryPeerRepository()), REGISTRATION_TOKEN) }

            val response = client.send(HttpMethod.Post, "/api/peers", INVALID_KEY_BODY, REGISTRATION_TOKEN)

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "canonical, non-zero WireGuard public key")
        }

    @Test
    fun `rejects an oversized body`() =
        testApplication {
            application { peerApi(PeerDirectory(InMemoryPeerRepository()), REGISTRATION_TOKEN) }

            val body = "x".repeat(OVERSIZED_BODY_LENGTH)
            val response = client.send(HttpMethod.Post, "/api/peers", body, REGISTRATION_TOKEN)

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `limits requests by client address`() =
        testApplication {
            application {
                peerApi(
                    PeerDirectory(InMemoryPeerRepository()),
                    REGISTRATION_TOKEN,
                    RateLimits(requestsPerMinute = 1),
                )
            }

            assertEquals(HttpStatusCode.OK, client.send(HttpMethod.Get, "/api/peers").status)
            val limited = client.send(HttpMethod.Get, "/api/peers")
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertNotNull(limited.headers[HttpHeaders.RetryAfter])
        }

    private suspend fun HttpClient.send(
        method: HttpMethod,
        path: String,
        body: String? = null,
        token: String? = null,
    ): HttpResponse =
        request(path) {
            this.method = method
            if (body != null) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(body)
            }
            if (token != null) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }

    private companion object {
        const val REGISTRATION_TOKEN = "registration-token-with-32-characters"
        const val CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy"
        const val X_CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
        const val REFERRER_POLICY_HEADER = "Referrer-Policy"
        const val CONTENT_SECURITY_POLICY =
            "default-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; object-src 'none'"
        const val PUBLIC_KEY = "TrMvSoP4jYQlY6RIzBgbssQqY3vxI2Pi+y71lOWWXX0="
        const val SECOND_PUBLIC_KEY = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBA="
        const val PEER_BODY =
            "{\"name\":\"alice\",\"address\":\"10.0.0.1/32\",\"publicKey\":\"$PUBLIC_KEY\"," +
                "\"endpoint\":\"alice.example.com:51820\"}"
        const val BOB_BODY =
            "{\"name\":\"bob\",\"address\":\"10.0.0.3/32\",\"publicKey\":\"$SECOND_PUBLIC_KEY\"," +
                "\"endpoint\":\"bob.example.com:51820\"}"
        const val DUPLICATE_ADDRESS_BODY =
            "{\"name\":\"bob\",\"address\":\"10.0.0.1/32\",\"publicKey\":\"$SECOND_PUBLIC_KEY\"," +
                "\"endpoint\":\"bob.example.com:51820\"}"
        const val UPDATE_BODY =
            "{\"address\":\"10.0.0.2/32\",\"publicKey\":\"$PUBLIC_KEY\"," +
                "\"endpoint\":\"alice.example.com:51821\"}"
        const val INVALID_KEY_BODY =
            "{\"name\":\"alice\",\"address\":\"10.0.0.1/32\",\"publicKey\":\"not-a-key\"," +
                "\"endpoint\":\"alice.example.com:51820\"}"
        const val OVERSIZED_BODY_LENGTH = 20_000
    }
}
