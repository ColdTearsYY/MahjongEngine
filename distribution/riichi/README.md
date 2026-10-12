# Riichi 2.0.2 upstream CI candidate

This directory redistributes **unchanged** rule and resource artifacts from the author's successful CI build. It is a fork testing distribution, **not an official upstream release**. Author attribution remains Ellan/ellan; MahjongPaper and its original notices are preserved.

- Source repository: https://github.com/EllanServer/riichi-mahjong-java
- Source commit: `fa2268a0516c60e40d731085227587b7f68de037`
- CI run: https://github.com/EllanServer/riichi-mahjong-java/actions/runs/32442492026
- Artifact: `riichi-mahjong-java-candidate`, artifact ID `9432942356`
- GitHub artifact ZIP digest: `sha256:41a51c66d0c1416a7d639f3dc9f59a0457c58d55bac3a5ae6465daa7ea179ec2`

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `2.0.2/riichi-rule-pack-2.0.2.jar` | 288261 | `4ee3962503c0f418202af7eb8c35a1a6f4e7ee723ec31e1c3a765020597c70eb` |
| `2.0.2/riichi-resource-pack-2.0.2.zip` | 98267 | `42b35c1cdbdf0020f21bb82304c548d05aa675bc1472543ab23843174bd2db15` |

`registry.json` adds this code/resource pair to the existing official entries and is signed by the **fork** Ed25519 signer. It must not be described as officially signed. The build retains the original official signer and adds the explicitly pinned fork public key:

```text
MCowBQYDK2VwAyEAwQzKwTZ1wK3f/hkA2mH9cWgDSjaPpzSEYQHvQu7w9mU=
```

Unknown signers, altered payloads, mismatched SHA-256/size, unsupported SPI and forbidden bytecode remain rejected. No private key is distributed. A signing secret is stored in the fork's GitHub Actions secrets for maintenance; the normal build/test workflow does not read it.

The code is covered by the upstream MIT license in `LICENSE.riichi-upstream.txt` (copyright Arbousier1). Resource attribution is included in the ZIP's `META-INF/RESOURCEPACK_ATTRIBUTION.md` and remains authoritative for the resources. Source and license locations are linked above. See [the enabling guide](../../docs/riichi-lophine-26.3.zh-CN.md) for deployment and verification limits.
