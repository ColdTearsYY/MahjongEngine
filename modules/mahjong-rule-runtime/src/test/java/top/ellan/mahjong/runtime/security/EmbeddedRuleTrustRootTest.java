package top.ellan.mahjong.runtime.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import top.ellan.mahjong.runtime.common.RulePackException;

class EmbeddedRuleTrustRootTest {
    private static final byte[] PAYLOAD = "signed registry payload".getBytes(StandardCharsets.UTF_8);

    @Test
    void retainsOfficialSignerAndAcceptsOnlyTheExplicitForkSigner() throws Exception {
        KeyPair official = key();
        KeyPair fork = key();
        OfficialTrustRoot trust = load(encoded(official), encoded(fork));
        assertDoesNotThrow(() -> trust.verify(PAYLOAD, sign(official, PAYLOAD)));
        assertDoesNotThrow(() -> trust.verify(PAYLOAD, sign(fork, PAYLOAD)));
        KeyPair stranger = key();
        assertThrows(RulePackException.class, () -> trust.verify(PAYLOAD, sign(stranger, PAYLOAD)));
    }

    @Test
    void rejectsTamperedPayloadAndMalformedSignatureWithBothRoots() throws Exception {
        KeyPair official = key();
        KeyPair fork = key();
        OfficialTrustRoot trust = load(encoded(official), encoded(fork));
        byte[] signature = sign(fork, PAYLOAD);
        byte[] changed = PAYLOAD.clone();
        changed[0] ^= 1;
        assertThrows(RulePackException.class, () -> trust.verify(changed, signature));
        assertThrows(RulePackException.class, () -> trust.verify(PAYLOAD, new byte[3]));
    }

    @Test
    void absentOrEmptyForkRootKeepsSingleSignerBehavior() throws Exception {
        KeyPair official = key();
        KeyPair stranger = key();
        for (String fork : new String[] {null, "\n"}) {
            OfficialTrustRoot trust = load(encoded(official), fork);
            assertDoesNotThrow(() -> trust.verify(PAYLOAD, sign(official, PAYLOAD)));
            assertThrows(RulePackException.class, () -> trust.verify(PAYLOAD, sign(stranger, PAYLOAD)));
        }
    }

    @Test
    void forkRootCannotReplaceMissingOrInvalidOfficialRoot() throws Exception {
        String fork = encoded(key());
        assertThrows(RulePackException.class, () -> load(null, fork));
        assertThrows(RulePackException.class, () -> load("", fork));
        assertThrows(RulePackException.class, () -> load("not a public key", fork));
    }

    @Test
    void rejectsInvalidOrOversizedForkRootInsteadOfSilentlyIgnoringIt() throws Exception {
        String official = encoded(key());
        assertThrows(RulePackException.class, () -> load(official, "bad key"));
        assertThrows(RulePackException.class, () -> load(official, "A".repeat(8_193)));
    }

    @Test
    void rejectsPrivateMaterialAndNonEd25519Keys() throws Exception {
        KeyPair official = key();
        String privateKey = Base64.getEncoder().encodeToString(official.getPrivate().getEncoded());
        assertThrows(RulePackException.class, () -> load(encoded(official), privateKey));
        KeyPair rsa = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        assertThrows(RulePackException.class, () -> load(encoded(official), encoded(rsa)));
    }

    private static OfficialTrustRoot load(String official, String fork) throws Exception {
        Map<String, String> resources = new HashMap<>();
        if (official != null) resources.put(EmbeddedRuleTrustRoot.RESOURCE, official);
        if (fork != null) resources.put(EmbeddedRuleTrustRoot.FORK_RESOURCE, fork);
        return EmbeddedRuleTrustRoot.load(new ClassLoader(null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                String value = resources.get(name);
                return value == null ? null
                        : new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII));
            }
        });
    }

    private static KeyPair key() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static String encoded(KeyPair pair) {
        return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    private static byte[] sign(KeyPair pair, byte[] payload) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(payload);
        return signer.sign();
    }
}
