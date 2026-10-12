package top.ellan.mahjong.runtime.security;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import top.ellan.mahjong.runtime.common.RulePackException;

/** Loads the release-build Ed25519 public key. Empty development builds fail closed. */
public final class EmbeddedRuleTrustRoot {
    public static final String RESOURCE = "META-INF/mahjong-rule-trust-root.txt";
    public static final String FORK_RESOURCE = "META-INF/mahjong-fork-rule-trust-root.txt";

    private EmbeddedRuleTrustRoot() {}

    public static OfficialTrustRoot load(ClassLoader classLoader)
            throws IOException, RulePackException {
        Objects.requireNonNull(classLoader, "classLoader");
        String primary = read(classLoader, RESOURCE);
        if (primary == null || primary.isEmpty()) {
            throw new RulePackException("Rule-pack trust root is not configured for this build");
        }
        OfficialTrustRoot official = OfficialTrustRoot.fromBase64X509(primary);
        String fork = read(classLoader, FORK_RESOURCE);
        return fork == null || fork.isEmpty()
                ? official
                : official.withAdditionalRoot(OfficialTrustRoot.fromBase64X509(fork));
    }

    private static String read(ClassLoader classLoader, String resource)
            throws IOException, RulePackException {
        try (InputStream input = classLoader.getResourceAsStream(resource)) {
            if (input == null) {
                return null;
            }
            byte[] bytes = input.readNBytes(8_193);
            if (bytes.length > 8_192) {
                throw new RulePackException("Embedded rule-pack trust root is oversized");
            }
            return new String(bytes, StandardCharsets.US_ASCII).trim();
        }
    }
}
