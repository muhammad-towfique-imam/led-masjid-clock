package hdsender.signing;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

/**
 * The release signing key of HD Sender. The key and its passwords are in git only as an encrypted file
 * (keystore-backup/release-key.enc). The passphrase comes from the environment variable
 * HDSENDER_KEY_PASSPHRASE, or from the git-ignored file .env in the project folder.
 */
public final class ReleaseSigning {
    public static final String VAR = "HDSENDER_KEY_PASSPHRASE";
    public static final String SEALED = "keystore-backup/release-key.enc";
    private static final int MIN_PASSPHRASE = 16;

    public static final class Result {
        public final File storeFile;
        public final String storePassword;
        public final String keyAlias;
        public final String keyPassword;
        public final String source;

        Result(File storeFile, String storePassword, String keyAlias, String keyPassword, String source) {
            this.storeFile = storeFile;
            this.storePassword = storePassword;
            this.keyAlias = keyAlias;
            this.keyPassword = keyPassword;
            this.source = source;
        }
    }

    private ReleaseSigning() {}

    /**
     * The signing data, or null if there is no key. Order: the encrypted file with the passphrase,
     * then keystore.properties. A wrong passphrase throws, so that a release is never signed by mistake
     * with a different key or left unsigned without a message.
     */
    public static Result load(File projectDir, Map<String, String> environment) {
        String passphrase = passphrase(projectDir, environment);
        File sealed = new File(projectDir, SEALED);
        if (passphrase != null && sealed.isFile()) {
            ReleaseKey key;
            try {
                key = ReleaseKey.fromBytes(KeyBox.open(Files.readAllBytes(sealed.toPath()), passphrase.toCharArray()));
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(
                    "Cannot decrypt " + SEALED + ": the passphrase in " + VAR + " (environment or .env) is wrong.");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            File storeFile = new File(projectDir, "build/signing/release.jks");
            try {
                Files.createDirectories(storeFile.getParentFile().toPath());
                Files.write(storeFile.toPath(), key.keystore);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new Result(storeFile, key.storePassword, key.keyAlias, key.keyPassword, SEALED);
        }
        File properties = new File(projectDir, "keystore.properties");
        if (properties.isFile()) {
            Properties p = readProperties(properties);
            return new Result(new File(projectDir, p.getProperty("storeFile")), p.getProperty("storePassword"),
                p.getProperty("keyAlias"), p.getProperty("keyPassword"), "keystore.properties");
        }
        return null;
    }

    /** Makes keystore-backup/release-key.enc from keystore.properties and its keystore, then checks it. */
    public static File encrypt(File projectDir, Map<String, String> environment) {
        String passphrase = passphrase(projectDir, environment);
        if (passphrase == null) {
            throw new IllegalStateException("Set " + VAR + " in the .env file (or the environment) first.");
        }
        if (passphrase.length() < MIN_PASSPHRASE) {
            throw new IllegalStateException(VAR + " must have at least " + MIN_PASSPHRASE + " characters.");
        }
        Properties p = readProperties(new File(projectDir, "keystore.properties"));
        try {
            byte[] keystore = Files.readAllBytes(new File(projectDir, p.getProperty("storeFile")).toPath());
            ReleaseKey key = new ReleaseKey(keystore, p.getProperty("storePassword"),
                p.getProperty("keyAlias"), p.getProperty("keyPassword"));
            byte[] sealed = KeyBox.seal(key.toBytes(), passphrase.toCharArray(), new SecureRandom());
            ReleaseKey check = ReleaseKey.fromBytes(KeyBox.open(sealed, passphrase.toCharArray()));
            if (!Arrays.equals(check.keystore, keystore)) throw new IllegalStateException("check of the encrypted file failed");
            File out = new File(projectDir, SEALED);
            Files.createDirectories(out.getParentFile().toPath());
            Files.write(out.toPath(), sealed);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("encryption failed: " + e.getMessage(), e);
        }
    }

    private static String passphrase(File projectDir, Map<String, String> environment) {
        String value = environment.get(VAR);
        if (value == null || value.isEmpty()) value = DotEnv.read(new File(projectDir, ".env")).get(VAR);
        return value == null || value.isEmpty() ? null : value;
    }

    private static Properties readProperties(File file) {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return p;
    }
}
