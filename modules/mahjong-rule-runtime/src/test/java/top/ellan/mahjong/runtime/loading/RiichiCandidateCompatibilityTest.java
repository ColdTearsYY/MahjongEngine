package top.ellan.mahjong.runtime.loading;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.ellan.mahjong.runtime.common.RulePackException;
import top.ellan.mahjong.runtime.install.RulePackInstaller;
import top.ellan.mahjong.runtime.registry.RulePackRegistryEntry;
import top.ellan.mahjong.runtime.registry.SignedRegistryCodec;
import top.ellan.mahjong.runtime.security.Hashing;
import top.ellan.mahjong.runtime.security.OfficialTrustRoot;
import top.ellan.mahjong.runtime.storage.RulePackPaths;
import top.ellan.mahjong.spi.*;

/** Exercises unchanged upstream CI artifacts through the same installer and loader as the server. */
class RiichiCandidateCompatibilityTest {
    @TempDir Path temporaryDirectory;
    private static final RuleId RIICHI = new RuleId("riichi");
    private static final String CODE_SHA = "4ee3962503c0f418202af7eb8c35a1a6f4e7ee723ec31e1c3a765020597c70eb";
    private static final String RESOURCE_SHA = "42b35c1cdbdf0020f21bb82304c548d05aa675bc1472543ab23843174bd2db15";

    @Test
    void installsSignedCodeAndResourcesThenCreatesTransitionsAndRestoresSnapshots() throws Exception {
        Fixture fixture = fixture();
        RulePackPaths paths = new RulePackPaths(temporaryDirectory.resolve("rules"));
        RulePackInstaller installer = installer(fixture, paths, false);
        var installed = installer.install(RIICHI, Optional.of("2.0.2"));
        assertFalse(installed.alreadyInstalled());
        assertTrue(installed.resourceArtifact().isPresent());
        assertEquals(CODE_SHA, Hashing.sha256(installed.artifact()));
        assertEquals(RESOURCE_SHA, Hashing.sha256(installed.resourceArtifact().orElseThrow()));
        assertTrue(installer.install(RIICHI, Optional.of("2.0.2")).alreadyInstalled());
        RulePackRegistryEntry expected = SignedRegistryCodec.decode(fixture.envelope(), fixture.trust())
                .registry().find(RIICHI, Optional.of("2.0.2")).orElseThrow();
        try (LoadedRulePack loaded = new RulePackLoader("2.0.0").load(installed.artifact(), expected)) {
            RulePackProvider provider = loaded.provider();
            List<MatchPlayer> players = new ArrayList<>();
            for (int seat = 0; seat < 4; seat++) {
                players.add(new MatchPlayer(new PlayerId(new UUID(0, seat + 1)), new SeatId(seat)));
            }
            MatchSetup setup = new MatchSetup(new ProfileId("mahjong-soul"),
                    new MatchSeed(123, 456), players, Map.of());
            RuleState initial = call(() -> provider.createMatch(setup));
            assertEquals(call(() -> provider.stateHash(provider.createMatch(setup))),
                    call(() -> provider.stateHash(initial)));
            RuleState state = initial;
            for (int step = 0; step < 100; step++) {
                RuleState current = state;
                assertNotNull(call(() -> provider.publicView(current, 0)));
                for (MatchPlayer player : players) {
                    assertNotNull(call(() -> provider.privateView(current, player.playerId(), 0)));
                }
                RuleStateSnapshot snapshot = call(() -> provider.snapshot(current, 0));
                RuleState restored = call(() -> provider.restore(snapshot));
                assertEquals(call(() -> provider.stateHash(current)), call(() -> provider.stateHash(restored)));
                Optional<ScheduledRuleAction> scheduled = call(() -> provider.scheduledAction(current));
                PlayerId actor = null;
                RuleAction action = null;
                if (scheduled.isPresent()) {
                    actor = scheduled.get().actor();
                    action = scheduled.get().action();
                } else {
                    for (MatchPlayer player : players) {
                        List<LegalAction> legal = call(() -> provider.legalActions(current, player.playerId()));
                        if (!legal.isEmpty()) {
                            actor = player.playerId();
                            action = legal.getFirst().action();
                            break;
                        }
                    }
                }
                assertNotNull(action, "No progress action at step " + step);
                PlayerId selectedActor = actor;
                RuleAction selectedAction = action;
                RuleTransition transition = call(() -> provider.transition(current, selectedActor, selectedAction));
                assertTrue(transition.accepted(), transition.reasonCode());
                state = transition.nextState();
            }
        }
    }

    @Test
    void rejectsResourceTamperingBeforePublishingInstalledDirectory() throws Exception {
        Fixture fixture = fixture();
        RulePackPaths paths = new RulePackPaths(temporaryDirectory.resolve("tampered"));
        assertThrows(RulePackException.class,
                () -> installer(fixture, paths, true).install(RIICHI, Optional.of("2.0.2")));
        assertFalse(Files.exists(paths.installedJar(RIICHI, "2.0.2")));
    }

    private RulePackInstaller installer(Fixture fixture, RulePackPaths paths, boolean tamper) {
        return new RulePackInstaller(paths, fixture::envelope, (uri, size, target) -> {
            Path source = uri.getPath().endsWith(".jar") ? fixture.code() : fixture.resources();
            if (tamper && source.equals(fixture.resources())) {
                byte[] bytes = Files.readAllBytes(source);
                bytes[bytes.length / 2] ^= 1;
                Files.write(target, bytes);
            } else {
                Files.copy(source, target);
            }
        }, fixture.trust(), new RulePackLoader("2.0.0"), Clock.systemUTC());
    }

    private Fixture fixture() throws Exception {
        String code = System.getenv("RIICHI_RULE_PACK_ARTIFACT");
        String resources = System.getenv("RIICHI_RESOURCE_PACK_ARTIFACT");
        Assumptions.assumeTrue(code != null && resources != null);
        Path jar = Path.of(code);
        Path zip = Path.of(resources);
        assertEquals(CODE_SHA, Hashing.sha256(jar));
        assertEquals(RESOURCE_SHA, Hashing.sha256(zip));
        KeyPair official = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair fork = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        OfficialTrustRoot trust = OfficialTrustRoot.fromX509(official.getPublic().getEncoded())
                .withAdditionalRoot(OfficialTrustRoot.fromX509(fork.getPublic().getEncoded()));
        String payload = "{\"format\":2,\"generatedAt\":\"2026-10-12T00:00:00Z\",\"packs\":[{"
                + "\"id\":\"riichi\",\"version\":\"2.0.2\",\"url\":\"https://ci.invalid/riichi.jar\","
                + "\"sha256\":\"" + CODE_SHA + "\",\"spiVersion\":\"1.6.0\","
                + "\"requiredCoreVersion\":\">=2.0.0\",\"sizeBytes\":" + Files.size(jar) + ","
                + "\"resourceUrl\":\"https://ci.invalid/riichi.zip\",\"resourceSha256\":\""
                + RESOURCE_SHA + "\",\"resourceSizeBytes\":" + Files.size(zip) + "}]}";
        byte[] raw = payload.getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(fork.getPrivate());
        signer.update(raw);
        String envelope = "{\"format\":1,\"payload\":\"" + Base64.getEncoder().encodeToString(raw)
                + "\",\"signature\":\"" + Base64.getEncoder().encodeToString(signer.sign()) + "\"}";
        return new Fixture(jar, zip, envelope.getBytes(StandardCharsets.US_ASCII), trust);
    }

    private static <T> T call(java.util.function.Supplier<T> operation) {
        return RuleExecutionBudget.call(Duration.ofSeconds(2), operation);
    }

    private record Fixture(Path code, Path resources, byte[] envelope, OfficialTrustRoot trust) {}
}
