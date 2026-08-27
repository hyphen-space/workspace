# WireGuard peer directory

A static directory for public WireGuard peer addresses and keys. Git stores the
peer definitions. Hugo generates the page and downloadable WireGuard
configurations. GitHub Actions deploys the result to GitHub Pages.

## Add or update a peer

Add `site-data/peers/<name>.json`, or edit an existing file:

```json
{
  "name": "alice",
  "address": "10.8.0.2/32",
  "publicKey": "TrMvSoP4jYQlY6RIzBgbssQqY3vxI2Pi+y71lOWWXX0=",
  "endpoint": "alice.example.com:51820"
}
```

The file name must match `name`. Open a pull request. The Hugo build rejects
invalid identities and duplicate addresses or public keys. A merge to `main`
rebuilds and deploys the site.

`endpoint` is optional. Omit it for peers that only initiate connections or do
not want to publish a hostname.

## Generate locally

Install Hugo 0.164.0 or later, then run:

```bash
node scripts/validate-peers.mjs
hugo
```

The site is written to `build/site`. For local development:

```bash
hugo server
```

Each peer gets `build/site/configs/<name>-wg.conf`. The web page links to these
files. Users must replace `REPLACE_WITH_PRIVATE_KEY` after download.

## Deploy

In the repository settings, set the GitHub Pages source to **GitHub Actions**.
The workflow validates every change and deploys after a push to `main`.

## Security

Peer definitions, tunnel addresses, generated configurations, and public keys
are public. Never commit a WireGuard private key. Protect `main` with required
reviews and require MFA for repository writers. See [SECURITY.md](SECURITY.md).
