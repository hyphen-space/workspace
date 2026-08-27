#!/usr/bin/env bash

set -euo pipefail

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

key_alice='MOKlWrSKefD+Rfravw2iBhnmQvavMvznmMsMkmzkaP0='
key_bob='ujUKr5qa5vtpRqwN8mWXHgVAMjI02EPcU7TPVxxz3SA='

peer() {
  printf '{"name":"%s","address":"%s","publicKey":"%s","endpoint":"%s"}\n' \
    "$1" "$2" "$3" "$4"
}

invalid() {
  name=$1
  expected=$2
  shift 2
  data="$work/$name/data/peers"
  output="$work/$name/site"
  log="$work/$name/build.log"
  mkdir -p "$data"

  while [ "$#" -gt 0 ]; do
    file=$1
    content=$2
    shift 2
    printf '%s\n' "$content" > "$data/$file.json"
  done

  if HUGO_DATADIR="$work/$name/data" hugo --source "$root" \
    --destination "$output" --panicOnWarning > "$log" 2>&1; then
    echo "Expected $name to fail" >&2
    return 1
  fi

  if ! grep -Fq "$expected" "$log"; then
    echo "Wrong error for $name" >&2
    cat "$log" >&2
    return 1
  fi
}

invalid file-name 'must match data file name' \
  wrong "$(peer alice 192.0.2.1/32 "$key_alice" alice.example:51820)"
invalid peer-name 'has an invalid name' \
  'bad name' "$(peer 'bad name' 192.0.2.1/32 "$key_alice" alice.example:51820)"
invalid cidr 'has an invalid CIDR address' \
  alice "$(peer alice 192.0.2.999/32 "$key_alice" alice.example:51820)"
invalid public-key 'has an invalid WireGuard public key' \
  alice "$(peer alice 192.0.2.1/32 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=' alice.example:51820)"
invalid endpoint 'has an invalid endpoint port' \
  alice "$(peer alice 192.0.2.1/32 "$key_alice" alice.example:70000)"
invalid duplicate-address 'is not unique' \
  alice "$(peer alice 192.0.2.1/32 "$key_alice" alice.example:51820)" \
  bob "$(peer bob 192.0.2.1/32 "$key_bob" bob.example:51820)"
invalid duplicate-key 'is not unique' \
  alice "$(peer alice 192.0.2.1/32 "$key_alice" alice.example:51820)" \
  bob "$(peer bob 192.0.2.2/32 "$key_alice" bob.example:51820)"

echo 'Peer validation tests passed'
