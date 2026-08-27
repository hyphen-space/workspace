#!/usr/bin/env bash

set -euo pipefail

root=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

key_alice='MOKlWrSKefD+Rfravw2iBhnmQvavMvznmMsMkmzkaP0='
key_bob='ujUKr5qa5vtpRqwN8mWXHgVAMjI02EPcU7TPVxxz3SA='

peer() {
  printf '{"name":"%s","address":"%s","publicKey":"%s","endpoint":"%s"}\n' \
    "$1" "$2" "$3" "$4"
}

peer_without_endpoint() {
  printf '{"name":"%s","address":"%s","publicKey":"%s"}\n' \
    "$1" "$2" "$3"
}

test_optional_endpoint() {
  data="$work/no-endpoint/data/peers"
  output="$work/no-endpoint/site"
  mkdir -p "$data"
  peer alice 192.0.2.1/32 "$key_alice" alice.example:51820 \
    > "$data/alice.json"
  peer_without_endpoint bob 192.0.2.2/32 "$key_bob" \
    > "$data/bob.json"

  node "$root/scripts/validate-peers.mjs" "$data"
  HUGO_DATADIR="$work/no-endpoint/data" hugo --source "$root" \
    --destination "$output" --panicOnWarning >/dev/null

  if grep -q '^Endpoint = ' "$output/configs/alice-wg.conf"; then
    echo 'Generated a missing endpoint' >&2
    return 1
  fi

  grep -Fq 'Endpoint = alice.example:51820' "$output/configs/bob-wg.conf"
}

invalid() {
  name=$1
  expected=$2
  shift 2
  data="$work/$name/data/peers"
  log="$work/$name/build.log"
  mkdir -p "$data"

  while [ "$#" -gt 0 ]; do
    file=$1
    content=$2
    shift 2
    printf '%s\n' "$content" > "$data/$file.json"
  done

  if node "$root/scripts/validate-peers.mjs" "$data" > "$log" 2>&1; then
    echo "Expected $name to fail" >&2
    return 1
  fi

  if ! grep -Fq "$expected" "$log"; then
    echo "Wrong error for $name" >&2
    cat "$log" >&2
    return 1
  fi
}

node "$root/scripts/validate-peers.mjs" "$root/site-data/peers"
test_optional_endpoint

invalid file-name 'must match data file name' \
  wrong "$(peer alice 192.0.2.1/32 "$key_alice" alice.example:51820)"
invalid peer-name 'has an invalid name' \
  'bad name' "$(peer 'bad name' 192.0.2.1/32 "$key_alice" alice.example:51820)"
invalid cidr 'has an invalid CIDR address' \
  alice "$(peer alice 192.0.2.999/32 "$key_alice" alice.example:51820)"
invalid ipv6 'has an invalid CIDR address' \
  alice "$(peer alice '::::/64' "$key_alice" alice.example:51820)"
invalid public-key 'has an invalid WireGuard public key' \
  alice "$(peer alice 192.0.2.1/32 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=' alice.example:51820)"
invalid endpoint 'has an invalid endpoint port' \
  alice "$(peer alice 192.0.2.1/32 "$key_alice" alice.example:70000)"
invalid endpoint-host 'has an invalid endpoint' \
  alice "$(peer alice 192.0.2.1/32 "$key_alice" 'bad host:51820')"
invalid duplicate-address 'is not unique' \
  alice "$(peer alice 192.0.2.1/32 "$key_alice" alice.example:51820)" \
  bob "$(peer bob 192.0.2.1/32 "$key_bob" bob.example:51820)"
invalid duplicate-key 'is not unique' \
  alice "$(peer alice 192.0.2.1/32 "$key_alice" alice.example:51820)" \
  bob "$(peer bob 192.0.2.2/32 "$key_alice" bob.example:51820)"

invalid missing-field 'must contain name, address, publicKey' \
  alice '{"name":"alice","address":"192.0.2.1/32"}'
invalid malformed-json 'is not valid JSON' \
  alice '{'

echo 'Peer validation tests passed'
