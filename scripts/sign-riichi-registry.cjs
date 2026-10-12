'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const [officialPath, privateKeyPath, distributionPath] = process.argv.slice(2);
if (!officialPath || !privateKeyPath || !distributionPath) {
  throw new Error('Usage: node sign-riichi-registry.cjs <official-registry> <private-pkcs8-base64-file> <distribution-dir>');
}
const OFFICIAL = 'MCowBQYDK2VwAyEABKsqAQxbRheenJb5AsClim4f1uuCCrdByfaM5AOxlro=';
const FORK = 'MCowBQYDK2VwAyEAwQzKwTZ1wK3f/hkA2mH9cWgDSjaPpzSEYQHvQu7w9mU=';
const artifactNames = ['riichi-rule-pack-2.0.2.jar', 'riichi-resource-pack-2.0.2.zip'];
const hashes = ['4ee3962503c0f418202af7eb8c35a1a6f4e7ee723ec31e1c3a765020597c70eb', '42b35c1cdbdf0020f21bb82304c548d05aa675bc1472543ab23843174bd2db15'];
const sizes = [288261, 98267];
const publicKey = key => crypto.createPublicKey({key: Buffer.from(key, 'base64'), format: 'der', type: 'spki'});
const official = JSON.parse(fs.readFileSync(officialPath, 'utf8'));
const original = Buffer.from(official.payload, 'base64');
if (!crypto.verify(null, original, publicKey(OFFICIAL), Buffer.from(official.signature, 'base64'))) {
  throw new Error('Official registry signature is invalid');
}
const privateKey = crypto.createPrivateKey({key: Buffer.from(fs.readFileSync(privateKeyPath, 'utf8').trim(), 'base64'), format: 'der', type: 'pkcs8'});
if (crypto.createPublicKey(privateKey).export({format: 'der', type: 'spki'}).toString('base64') !== FORK) {
  throw new Error('Signing key does not match build-pinned fork public key');
}
for (let i = 0; i < artifactNames.length; i++) {
  const artifact = fs.readFileSync(path.join(distributionPath, '2.0.2', artifactNames[i]));
  if (artifact.length !== sizes[i] || crypto.createHash('sha256').update(artifact).digest('hex') !== hashes[i]) {
    throw new Error('Pinned upstream artifact size or SHA256 mismatch: ' + artifactNames[i]);
  }
}
const registry = JSON.parse(original.toString('utf8'));
registry.format = 2;
registry.generatedAt = new Date().toISOString();
registry.packs = registry.packs.filter(p => !(p.id === 'riichi' && p.version === '2.0.2'));
const url = 'https://raw.githubusercontent.com/ColdTearsYY/MahjongEngine/refs/heads/fix/lophine-26.3-compat/distribution/riichi/2.0.2/';
registry.packs.push({id: 'riichi', version: '2.0.2', url: url + artifactNames[0], sha256: hashes[0], spiVersion: '1.6.0', requiredCoreVersion: '>=2.0.0', sizeBytes: sizes[0], resourceUrl: url + artifactNames[1], resourceSha256: hashes[1], resourceSizeBytes: sizes[1]});
const payload = Buffer.from(JSON.stringify(registry), 'utf8');
const signature = crypto.sign(null, payload, privateKey);
if (!crypto.verify(null, payload, publicKey(FORK), signature)) throw new Error('Signing self-check failed');
const envelope = {format: 1, payload: payload.toString('base64'), signature: signature.toString('base64')};
const output = path.join(distributionPath, 'registry.json');
fs.writeFileSync(output, JSON.stringify(envelope, null, 2) + '\n');
console.log(JSON.stringify({output, signerPublicKey: FORK, packCount: registry.packs.length, sha256: crypto.createHash('sha256').update(fs.readFileSync(output)).digest('hex')}, null, 2));
