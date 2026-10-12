package top.ellan.mahjong.runtime.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import top.ellan.mahjong.runtime.common.RulePackException;

/** Build-pinned Ed25519 public trust roots; no private material is accepted here. */
public final class OfficialTrustRoot {
    private final List<PublicKey> publicKeys;

    private OfficialTrustRoot(List<PublicKey> publicKeys) {
        this.publicKeys = List.copyOf(publicKeys);
    }

    public static OfficialTrustRoot fromX509(byte[] encoded) throws RulePackException {
        Objects.requireNonNull(encoded, "encoded");
        try {
            PublicKey key =
                    KeyFactory.getInstance("Ed25519")
                            .generatePublic(new X509EncodedKeySpec(encoded.clone()));
            return new OfficialTrustRoot(List.of(key));
        } catch (GeneralSecurityException failure) {
            throw new RulePackException("Invalid Ed25519 trust root", failure);
        }
    }

    public static OfficialTrustRoot fromBase64X509(String encoded) throws RulePackException {
        try {
            return fromX509(Base64.getDecoder().decode(Objects.requireNonNull(encoded, "encoded")));
        } catch (IllegalArgumentException failure) {
            throw new RulePackException("Trust root is not valid Base64", failure);
        }
    }

    /** Adds an explicitly build-pinned fork signer without replacing the official signer. */
    public OfficialTrustRoot withAdditionalRoot(OfficialTrustRoot additional) {
        Objects.requireNonNull(additional, "additional");
        return new OfficialTrustRoot(java.util.stream.Stream.concat(
                        publicKeys.stream(), additional.publicKeys.stream())
                .distinct().toList());
    }

    public void verify(byte[] payload, byte[] signature) throws RulePackException {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(signature, "signature");
        try {
            for (PublicKey publicKey : publicKeys) {
                Signature verifier = Signature.getInstance("Ed25519");
                verifier.initVerify(publicKey);
                verifier.update(payload);
                if (verifier.verify(signature)) {
                    return;
                }
            }
            throw new RulePackException("Registry Ed25519 signature is invalid");
        } catch (GeneralSecurityException failure) {
            throw new RulePackException("Unable to verify registry signature", failure);
        }
    }
}
