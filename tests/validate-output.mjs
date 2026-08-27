#!/usr/bin/env node

import { readFile, readdir } from "node:fs/promises";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const sourceDirectory = resolve(root, "site-data/peers");
const siteDirectory = resolve(root, "build/site");
const placeholder = "REPLACE_WITH_PRIVATE_KEY";

const sourceFiles = (await readdir(sourceDirectory))
  .filter((file) => file.endsWith(".json"))
  .sort();
const sourcePeers = await Promise.all(sourceFiles.map(async (file) =>
  JSON.parse(await readFile(resolve(sourceDirectory, file), "utf8"))));
const publishedPeers = JSON.parse(await readFile(resolve(siteDirectory, "peers.json"), "utf8"));
const normalize = (peers) => peers.map((peer) => Object.fromEntries(
  Object.entries(peer).sort(([left], [right]) => left.localeCompare(right)),
));

if (JSON.stringify(normalize(publishedPeers)) !== JSON.stringify(normalize(sourcePeers))) {
  throw new Error("Published peers.json does not match the source data");
}

const html = await readFile(resolve(siteDirectory, "index.html"), "utf8");
const expectedConfigs = sourcePeers.map((peer) => `${peer.name}-wg.conf`).sort();
const actualConfigs = (await readdir(resolve(siteDirectory, "configs"))).sort();

if (JSON.stringify(actualConfigs) !== JSON.stringify(expectedConfigs)) {
  throw new Error("Generated configuration files do not match the peers");
}

if (!html.includes('href=/workspace/index.css')) {
  throw new Error("The page does not link to the deployed stylesheet");
}

for (const owner of sourcePeers) {
  const link = `href=/workspace/configs/${owner.name}-wg.conf`;

  if (!html.includes(link)) {
    throw new Error(`The page does not link to ${owner.name}'s configuration`);
  }

  const config = await readFile(resolve(siteDirectory, "configs", `${owner.name}-wg.conf`), "utf8");
  const privateKeys = config.match(/^PrivateKey\s*=.*$/gm) ?? [];

  if (privateKeys.length !== 1 || privateKeys[0] !== `PrivateKey = ${placeholder}`) {
    throw new Error(`${owner.name}'s configuration contains an unsafe private-key value`);
  }

  if (!config.includes(`Address = ${owner.address}`)) {
    throw new Error(`${owner.name}'s configuration has the wrong address`);
  }

  for (const peer of sourcePeers.filter((candidate) => candidate.name !== owner.name)) {
    for (const line of [
      `PublicKey = ${peer.publicKey}`,
      `AllowedIPs = ${peer.address}`,
      `Endpoint = ${peer.endpoint}`,
    ]) {
      if (!config.includes(line)) {
        throw new Error(`${owner.name}'s configuration is missing ${peer.name}'s data`);
      }
    }
  }
}

console.log("Generated output tests passed");
