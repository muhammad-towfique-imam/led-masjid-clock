package hdsender.signing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Properties;

/** The release keystore and its passwords, in 1 block of bytes (the content of the encrypted file). */
public final class ReleaseKey {
    public final byte[] keystore;
    public final String storePassword;
    public final String keyAlias;
    public final String keyPassword;

    public ReleaseKey(byte[] keystore, String storePassword, String keyAlias, String keyPassword) {
        this.keystore = keystore;
        this.storePassword = storePassword;
        this.keyAlias = keyAlias;
        this.keyPassword = keyPassword;
    }

    public byte[] toBytes() {
        Properties p = new Properties();
        p.setProperty("keystore", Base64.getEncoder().encodeToString(keystore));
        p.setProperty("storePassword", storePassword);
        p.setProperty("keyAlias", keyAlias);
        p.setProperty("keyPassword", keyPassword);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            p.store(out, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public static ReleaseKey fromBytes(byte[] bytes) {
        Properties p = new Properties();
        try {
            p.load(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ReleaseKey(
            Base64.getDecoder().decode(required(p, "keystore")),
            required(p, "storePassword"),
            required(p, "keyAlias"),
            required(p, "keyPassword"));
    }

    private static String required(Properties p, String name) {
        String value = p.getProperty(name);
        if (value == null) throw new IllegalArgumentException("missing " + name);
        return value;
    }
}
