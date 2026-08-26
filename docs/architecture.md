# Architecture

```text
HTTP adapter -> peer application -> peer domain
                    |
              repository port
                    |
             SQLite adapter
```

- `peer.domain` owns peer validation and the repository port.
- `peer.application` owns registration, authorization, and lifecycle actions.
- `peer.infrastructure` stores peers and token hashes in SQLite.
- `http` uses Ktor to map REST requests to application actions and serve the
  web page. Ktor owns JSON conversion, authentication, rate limits, and error
  responses.

Public reads use Ktor's per-address token-bucket limits. Registration needs the server's
`REGISTRATION_TOKEN` and has a stricter limit. Update and deletion use the
peer-specific owner token.

The API returns public keys, never private keys. Registration creates a random
owner token. Only its SHA-256 hash is stored. The token permits changes to one
peer record. Config downloads contain a private-key placeholder that the owner
must replace locally.

SQLite uses write-ahead logging and a busy timeout for concurrent requests. Add
TLS at a reverse proxy because bearer tokens must not cross plain public HTTP.
