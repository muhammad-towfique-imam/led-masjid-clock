package hdsender.signing;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Passphrase encryption with the Java standard library: AES-256-GCM with a key from PBKDF2-HMAC-SHA256.
 * Format: "HDKEY1" | iterations (4 bytes) | salt (16) | nonce (12) | ciphertext with GCM tag.
 */
public final class KeyBox {
    private static final byte[] MAGIC = "HDKEY1".getBytes(StandardCharsets.US_ASCII);
    private static final int ITERATIONS = 310_000;
    private static final int SALT = 16;
    private static final int NONCE = 12;
    private static final int TAG_BITS = 128;

    private KeyBox() {}

    public static byte[] seal(byte[] plain, char[] passphrase, SecureRandom random) throws GeneralSecurityException {
        byte[] salt = new byte[SALT];
        byte[] nonce = new byte[NONCE];
        random.nextBytes(salt);
        random.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, derive(passphrase, salt, ITERATIONS), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(MAGIC);
        byte[] sealed = cipher.doFinal(plain);
        return ByteBuffer.allocate(MAGIC.length + 4 + SALT + NONCE + sealed.length)
            .put(MAGIC).putInt(ITERATIONS).put(salt).put(nonce).put(sealed).array();
    }

    /** Throws GeneralSecurityException for a wrong passphrase, changed data, or a file of a different format. */
    public static byte[] open(byte[] data, char[] passphrase) throws GeneralSecurityException {
        if (data.length < MAGIC.length + 4 + SALT + NONCE + TAG_BITS / 8
            || !Arrays.equals(Arrays.copyOf(data, MAGIC.length), MAGIC)) {
            throw new GeneralSecurityException("not an HD Sender key file");
        }
        ByteBuffer in = ByteBuffer.wrap(data, MAGIC.length, data.length - MAGIC.length);
        int iterations = in.getInt();
        if (iterations < 100_000 || iterations > 10_000_000) throw new GeneralSecurityException("bad key file header");
        byte[] salt = new byte[SALT];
        byte[] nonce = new byte[NONCE];
        in.get(salt).get(nonce);
        byte[] sealed = new byte[in.remaining()];
        in.get(sealed);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, derive(passphrase, salt, iterations), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(MAGIC);
        return cipher.doFinal(sealed);
    }

    private static SecretKey derive(char[] passphrase, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, iterations, 256);
        try {
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(key, "AES");
        } finally {
            spec.clearPassword();
        }
    }
}
