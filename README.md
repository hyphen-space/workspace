# WireGuard peer directory

A small public directory for WireGuard peer addresses and public keys. Clients
can poll the REST API. Each peer gets a private bearer token for updates and
deletion.

The HTTP server uses Ktor with CIO, Kotlin serialization, bearer
authentication, and token-bucket rate limits.

Peer records and owner-token hashes are stored in SQLite.

## Run

Requires JDK 25. No root access or external service is needed.

```bash
export REGISTRATION_TOKEN="$(openssl rand -hex 32)"
./gradlew run
```

Open <http://localhost:8080>. Set `PORT` to use another port.

The default database is `data/wgkeys.db`. Set `DATABASE_PATH` to use another
file.

`REGISTRATION_TOKEN` must contain at least 32 characters. Share it only with
people or systems that can create peers.

## Container

Build and run the distribution image with Podman:

```bash
podman build -t wgkeys .
export REGISTRATION_TOKEN="$(openssl rand -hex 32)"
podman run --rm --name wgkeys \
  -p 8080:8080 \
  -e REGISTRATION_TOKEN \
  -v wgkeys-data:/data:Z \
  wgkeys
```

The image uses JDK 25 and runs as the unprivileged `wgkeys` user. The named
volume keeps the SQLite database after the container stops.

## API

Register a peer. Save the returned token: the server shows it only once.

```bash
curl -sS http://localhost:8080/api/peers \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $REGISTRATION_TOKEN" \
  -d '{
    "name": "alice",
    "address": "10.8.0.2/32",
    "publicKey": "TrMvSoP4jYQlY6RIzBgbssQqY3vxI2Pi+y71lOWWXX0=",
    "endpoint": "alice.example.com:51820"
  }'
```

List peers or get one peer:

```bash
curl -sS http://localhost:8080/api/peers
curl -sS http://localhost:8080/api/peers/alice
```

Download a `wg-quick` configuration template for one peer:

```bash
curl -fOJ http://localhost:8080/api/peers/alice/wg.conf
```

Replace `REPLACE_WITH_PRIVATE_KEY` in the file with Alice's private key. The
server does not receive or store private keys. The web page also has a Download
button for each peer.

Update a peer:

```bash
curl -sS -X PUT http://localhost:8080/api/peers/alice \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $PEER_TOKEN" \
  -d '{
    "address": "10.8.0.2/32",
    "publicKey": "TrMvSoP4jYQlY6RIzBgbssQqY3vxI2Pi+y71lOWWXX0=",
    "endpoint": "alice.example.com:51820"
  }'
```

Delete a peer:

```bash
curl -sS -X DELETE http://localhost:8080/api/peers/alice \
  -H "Authorization: Bearer $PEER_TOKEN"
```

Names are 1–63 safe URL characters. Addresses must use IPv4 or IPv6 CIDR
notation. Public keys must use canonical WireGuard Base64 encoding, decode to
32 non-zero bytes, and end with `=`. Endpoints must use `host:port` or
`[IPv6]:port` notation. Peer names, addresses, and public keys must be unique.
Conflicting registration or update requests return `409 Conflict`.

An existing database that contains duplicate addresses or public keys cannot
start after this upgrade. Resolve those duplicates before you restart it.

The list response includes `ETag` and `Cache-Control` headers. Polling clients
can send `If-None-Match`; an unchanged directory returns `304 Not Modified`.

Each client address can make 120 requests per minute and 10 registration
requests per hour. A limited request returns `429 Too Many Requests` and a
`Retry-After` header. Run a distributed limiter at the reverse proxy when you
use more than one application instance.

## Check

```bash
./gradlew test
./gradlew check
```

`check` also runs KtLint, Detekt, and JaCoCo.

## Security

Public keys and tunnel addresses are public by design. Never upload a WireGuard
private key. Use HTTPS outside a trusted local network. See [SECURITY.md](SECURITY.md)
for reporting and deployment guidance.
