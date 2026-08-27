#!/usr/bin/env node

import { readFile, readdir } from "node:fs/promises";
import { isIP } from "node:net";
import { basename, resolve } from "node:path";

const requiredFields = ["address", "name", "publicKey"];
const allowedFields = [...requiredFields, "endpoint"].sort();
const namePattern = /^[A-Za-z0-9][A-Za-z0-9._-]{0,62}$/;
const keyPattern = /^[A-Za-z0-9+/]{43}=$/;
const labelPattern = /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$/;
const maxHostnameLength = 253;
const maxPort = 65535;

function fail(message) {
  throw new Error(message);
}

function validateAddress(peer) {
  const separator = peer.address.lastIndexOf("/");

  if (separator < 1) {
    fail(`Peer ${JSON.stringify(peer.name)} has an invalid CIDR address`);
  }

  const address = peer.address.slice(0, separator);
  const prefixText = peer.address.slice(separator + 1);
  const family = isIP(address);
  const prefix = Number(prefixText);
  const maxPrefix = family === 4 ? 32 : 128;

  if (!family || !/^\d+$/.test(prefixText) || prefix > maxPrefix) {
    fail(`Peer ${JSON.stringify(peer.name)} has an invalid CIDR address`);
  }
}

function validHost(host) {
  if (isIP(host) === 4) {
    return true;
  }

  if (!host || host.length > maxHostnameLength) {
    return false;
  }

  return host.split(".").every((label) => labelPattern.test(label));
}

function validateEndpoint(peer) {
  let host;
  let portText;

  if (peer.endpoint.startsWith("[")) {
    const match = peer.endpoint.match(/^\[([^\]]+)\]:(\d+)$/);

    if (!match || isIP(match[1]) !== 6) {
      fail(`Peer ${JSON.stringify(peer.name)} has an invalid endpoint`);
    }

    [, host, portText] = match;
  } else {
    const separator = peer.endpoint.lastIndexOf(":");
    host = peer.endpoint.slice(0, separator);
    portText = peer.endpoint.slice(separator + 1);

    if (separator < 1 || host.includes(":") || !validHost(host)) {
      fail(`Peer ${JSON.stringify(peer.name)} has an invalid endpoint`);
    }
  }

  const port = Number(portText);

  if (!/^\d+$/.test(portText) || port < 1 || port > maxPort) {
    fail(`Peer ${JSON.stringify(peer.name)} has an invalid endpoint port`);
  }
}

function validateKey(peer) {
  if (!keyPattern.test(peer.publicKey)) {
    fail(`Peer ${JSON.stringify(peer.name)} has an invalid WireGuard public key`);
  }

  const key = Buffer.from(peer.publicKey, "base64");

  if (key.length !== 32 || key.every((byte) => byte === 0)) {
    fail(`Peer ${JSON.stringify(peer.name)} has an invalid WireGuard public key`);
  }
}

function validatePeer(file, peer) {
  if (!peer || typeof peer !== "object" || Array.isArray(peer)) {
    fail(`${file} must contain a peer object`);
  }

  const actualFields = Object.keys(peer).sort();

  if (actualFields.some((field) => !allowedFields.includes(field)) ||
      requiredFields.some((field) => !actualFields.includes(field))) {
    fail(`${file} must contain name, address, publicKey, and an optional endpoint`);
  }

  if (actualFields.some((field) => typeof peer[field] !== "string")) {
    fail(`${file} fields must be strings`);
  }

  const fileName = basename(file, ".json");

  if (fileName !== peer.name) {
    fail(`Peer name ${JSON.stringify(peer.name)} must match data file name ${JSON.stringify(fileName)}`);
  }

  if (!namePattern.test(peer.name)) {
    fail(`Peer ${JSON.stringify(peer.name)} has an invalid name`);
  }

  validateAddress(peer);
  validateKey(peer);
  if (peer.endpoint !== undefined) {
    validateEndpoint(peer);
  }
}

async function loadPeers(directory) {
  const files = (await readdir(directory))
    .filter((file) => file.endsWith(".json"))
    .sort();

  if (files.length === 0) {
    fail("Peer data directory has no JSON files");
  }

  return Promise.all(files.map(async (file) => {
    const path = resolve(directory, file);
    let peer;

    try {
      peer = JSON.parse(await readFile(path, "utf8"));
    } catch (error) {
      fail(`${file} is not valid JSON: ${error.message}`);
    }

    validatePeer(file, peer);
    return peer;
  }));
}

function validateUnique(peers) {
  const addresses = new Set();
  const keys = new Set();

  for (const peer of peers) {
    if (addresses.has(peer.address)) {
      fail(`Peer address ${JSON.stringify(peer.address)} is not unique`);
    }

    if (keys.has(peer.publicKey)) {
      fail(`Peer public key for ${JSON.stringify(peer.name)} is not unique`);
    }

    addresses.add(peer.address);
    keys.add(peer.publicKey);
  }
}

const directory = resolve(process.argv[2] ?? "site-data/peers");

try {
  validateUnique(await loadPeers(directory));
  console.log(`Validated peer data in ${directory}`);
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
